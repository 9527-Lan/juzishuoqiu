package com.tencent.wxcloudrun.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.dao.ArticleMapper;
import com.tencent.wxcloudrun.dao.CommentMapper;
import com.tencent.wxcloudrun.dao.SysConfigMapper;
import com.tencent.wxcloudrun.dao.UserMapper;
import com.tencent.wxcloudrun.model.Article;
import com.tencent.wxcloudrun.model.Comment;
import com.tencent.wxcloudrun.model.SysConfig;
import com.tencent.wxcloudrun.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;

import javax.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCmsController {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Value("${wx.miniprogram.appid:wx8ac8fb0b363e193e}")
    private String wxAppid;

    @Value("${wx.miniprogram.secret:7ff2d70328143787f164868c87a9a12f}")
    private String wxSecret;

    private WxMaService wxMaService;

    private synchronized WxMaService getWxMaService() {
        if (wxMaService == null) {
            WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
            config.setAppid(wxAppid);
            config.setSecret(wxSecret);
            wxMaService = new WxMaServiceImpl();
            wxMaService.setWxMaConfig(config);
        }
        return wxMaService;
    }

    /**
     * 管理后台账号登录接口 (对应 orange888 或新保存密码)
     */
    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (!"admin".equals(username)) {
            return ApiResponse.error("管理账号不匹配！");
        }

        String actualPassword = sysConfigMapper.getValueByKey("admin_password");
        if (actualPassword == null) {
            actualPassword = "orange888"; // 降级默认密码
        }

        if (actualPassword.equals(password)) {
            // 使用 Sa-Token 登录超级管理员
            StpUtil.login("admin");
            // 获取管理员的 Token 凭证
            String tokenValue = StpUtil.getTokenValue();

            Map<String, String> result = new HashMap<>();
            result.put("token", tokenValue);
            return ApiResponse.ok(result);
        }

        return ApiResponse.error("管理员登录密码错误！");
    }

    /**
     * 图片上传接口（支持富文本与小程序头像上传）
     */
    @PostMapping("/upload")
    public ApiResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("上传文件不能为空");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String suffix = (originalFilename != null && originalFilename.contains(".")) 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = UUID.randomUUID().toString() + suffix;

            String envId = System.getenv("CBR_ENV_ID");
            if (envId == null || envId.trim().isEmpty()) {
                envId = System.getenv("WX_ENV_ID");
            }
            if (envId == null || envId.trim().isEmpty()) {
                envId = "prod-d4gmc9oke67a3ac4e";
            }

            String storagePath = "uploads/" + filename;
            String accessToken = getWxMaService().getAccessToken();

            JSONObject requestBody = JSONUtil.createObj();
            requestBody.set("env", envId);
            requestBody.set("path", storagePath);

            String requestUrl = "https://api.weixin.qq.com/tcb/uploadfile?access_token=" + accessToken;
            String responseStr = HttpUtil.post(requestUrl, requestBody.toString());
            JSONObject responseJson = JSONUtil.parseObj(responseStr);

            if (responseJson == null) {
                return ApiResponse.error("微信存储接口无响应");
            }
            if (!Integer.valueOf(0).equals(responseJson.getInt("errcode"))) {
                return ApiResponse.error("获取微信上传链接失败: " + responseJson.getStr("errmsg"));
            }

            String uploadUrl = responseJson.getStr("url");
            String signature = responseJson.getStr("authorization");
            String token = responseJson.getStr("token");
            String cosFileId = responseJson.getStr("cos_file_id");
            String fileId = responseJson.getStr("file_id");

            if (fileId == null || fileId.trim().isEmpty()) {
                fileId = cosFileId;
            }

            if (fileId != null && fileId.startsWith("cloud://") && fileId.contains("/")) {
                try {
                    String host = fileId.substring(8, fileId.indexOf("/", 8));
                    FileViewController.bucketHost = host;
                } catch (Exception ignored) {}
            }

            final String finalFilename = originalFilename != null ? originalFilename : "file.jpg";
            HttpResponse uploadResponse = HttpRequest.post(uploadUrl)
                .form("key", storagePath)
                .form("Signature", signature)
                .form("x-cos-security-token", token)
                .form("x-cos-meta-fileid", cosFileId)
                .form("file", file.getBytes(), finalFilename)
                .execute();

            if (!uploadResponse.isOk()) {
                return ApiResponse.error("上传至腾讯云存储失败，状态码: " + uploadResponse.getStatus());
            }

            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            String baseUrl = ("localhost".equals(serverName) || "127.0.0.1".equals(serverName))
                ? scheme + "://" + serverName + ":" + serverPort : scheme + "://" + serverName;

            String encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8");
            String fileUrl = baseUrl + "/download?fileId=" + encodedFileId;
            System.out.println("===> [图片上传] 上传成功！微信云存储文件ID=" + fileId + ", 中转访问链接: " + fileUrl);

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            return ApiResponse.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("文件上传发生异常: " + e.getMessage());
        }
    }

    /**
     * 根据 WeChat cloud:// FileID 或者是相对存储路径，换取带签名的临时公网下载链接
     * 对接官方 batchdownloadfile 接口，支持 2 小时有效期的带安全签名访问 URL。
     */
    @GetMapping("/download-url")
    public ApiResponse getDownloadUrl(@RequestParam("fileId") String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return ApiResponse.error("fileId 不能为空");
        }

        // 提取或配置微信云托管环境 ID
        String envId = System.getenv("CBR_ENV_ID");
        if (envId == null || envId.trim().isEmpty()) {
            envId = System.getenv("WX_ENV_ID");
        }
        if (envId == null || envId.trim().isEmpty()) {
            envId = "prod-d4gmc9oke67a3ac4e"; // 默认线上环境 ID
        }

        String targetFileId = fileId;
        // 如果传入的是相对路径 e.g. "uploads/abc.jpg"，自动拼装为完整的 cloud:// 格式
        if (!fileId.startsWith("cloud://")) {
            targetFileId = "cloud://" + envId + "/" + fileId;
        }

        try {
            // 获取 access_token
            String accessToken = getWxMaService().getAccessToken();

            JSONObject requestBody = JSONUtil.createObj();
            requestBody.set("env", envId);

            JSONObject fileObj = JSONUtil.createObj();
            fileObj.set("fileid", targetFileId);
            fileObj.set("max_age", 7200); // 2 小时有效时间

            requestBody.set("file_list", JSONUtil.createArray().set(fileObj));

            String requestUrl = "https://api.weixin.qq.com/tcb/batchdownloadfile?access_token=" + accessToken;
            String responseStr = HttpUtil.post(requestUrl, requestBody.toString());
            JSONObject responseJson = JSONUtil.parseObj(responseStr);

            if (responseJson != null && Integer.valueOf(0).equals(responseJson.getInt("errcode"))) {
                cn.hutool.json.JSONArray fileList = responseJson.getJSONArray("file_list");
                if (fileList != null && !fileList.isEmpty()) {
                    JSONObject fileResult = fileList.getJSONObject(0);
                    if (Integer.valueOf(0).equals(fileResult.getInt("status"))) {
                        Map<String, String> result = new HashMap<>();
                        result.put("downloadUrl", fileResult.getStr("download_url"));
                        return ApiResponse.ok(result);
                    } else {
                        return ApiResponse.error("微信云存储状态错误: " + fileResult.getStr("errmsg"));
                    }
                }
            }
            return ApiResponse.error("获取下载链接失败: " + (responseJson != null ? responseJson.getStr("errmsg") : "无响应"));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取下载链接异常: " + e.getMessage());
        }
    }

    // ================= 文章方案管理专区 =================
    @GetMapping("/article/list")
    public ApiResponse getArticleList(@RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestParam(value = "type", required = false) Integer type) {
        List<Article> list = articleMapper.findAllForAdmin(keyword, type);
        return ApiResponse.ok(list);
    }

    @PostMapping("/article/create")
    public ApiResponse createArticle(@RequestBody Article article) {
        if (article.getAuthor() == null || article.getAuthor().trim().isEmpty()) {
            article.setAuthor("橘子体育");
        }
        if (article.getPublishTime() == null) {
            article.setPublishTime(new Date());
        }
        articleMapper.insert(article);
        return ApiResponse.ok(article);
    }

    @PostMapping("/article/update")
    public ApiResponse updateArticle(@RequestBody Article article) {
        articleMapper.update(article);
        return ApiResponse.ok(article);
    }

    @DeleteMapping("/article/delete/{id}")
    public ApiResponse deleteArticle(@PathVariable("id") Long id) {
        articleMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ================= 评论管理专区 =================
    @GetMapping("/comment/list")
    public ApiResponse getCommentList(@RequestParam(value = "keyword", required = false) String keyword) {
        List<Comment> list = commentMapper.findAllForAdmin(keyword);
        return ApiResponse.ok(list);
    }

    @DeleteMapping("/comment/delete/{id}")
    public ApiResponse deleteComment(@PathVariable("id") Long id) {
        commentMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ================= 用户清单专区 =================
    @GetMapping("/user/list")
    public ApiResponse getUserList() {
        List<User> list = userMapper.findAll();
        return ApiResponse.ok(list);
    }

    /**
     * 分页查询特定用户的收藏赛事方案列表
     */
    @GetMapping("/user/favorites")
    public ApiResponse getUserFavorites(@RequestParam("userId") Long userId,
                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "limit", defaultValue = "5") int limit) {
        int total = articleMapper.countFavoritesByUserId(userId);
        int offset = (page - 1) * limit;
        List<java.util.Map<String, Object>> list = articleMapper.findFavoritesByUserIdWithPage(userId, offset, limit);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", list);
        return ApiResponse.ok(result);
    }

    // ================= 系统参数设置专区 =================
    @GetMapping("/config/list")
    public ApiResponse getConfigList() {
        List<SysConfig> list = sysConfigMapper.findAll();
        return ApiResponse.ok(list);
    }

    @PostMapping("/config/save")
    public ApiResponse saveConfigs(@RequestBody Map<String, String> body) {
        if (body.containsKey("service_hotline")) {
            sysConfigMapper.saveOrUpdate("service_hotline", body.get("service_hotline"));
        }
        if (body.containsKey("admin_password")) {
            sysConfigMapper.saveOrUpdate("admin_password", body.get("admin_password"));
        }
        if (body.containsKey("service_corp_id")) {
            sysConfigMapper.saveOrUpdate("service_corp_id", body.get("service_corp_id"));
        }
        if (body.containsKey("service_chat_url")) {
            sysConfigMapper.saveOrUpdate("service_chat_url", body.get("service_chat_url"));
        }
        return ApiResponse.ok();
    }
}
