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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
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
     * 图片上传接口，供富文本编辑器调用
     * 保存图片到本地 uploads/ 文件夹，并返回可通过 /uploads/xxx 直接加载的完整 URL 地址
     */
    @PostMapping("/upload")
    public ApiResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("上传文件不能为空");
        }

        try {
            // 确保本地 uploads 文件夹存在 (使用 getAbsoluteFile 获得绝对路径)
            File uploadDir = new File("uploads").getAbsoluteFile();
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 获取原文件名及后缀并生成 UUID 唯一文件名
            String originalFilename = file.getOriginalFilename();
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            } else {
                suffix = ".jpg"; // 兜底默认后缀
            }
            String filename = UUID.randomUUID().toString() + suffix;

            // 写入本地物理硬盘磁盘 (利用绝对路径强制落盘，使用 java.nio.file 规避 Tomcat 工作目录 Bug)
            File destFile = new File(uploadDir, filename).getAbsoluteFile();
            java.nio.file.Files.copy(file.getInputStream(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 拼接完整的可请求的图片链接
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            String baseUrl;
            if ("localhost".equals(serverName) || "127.0.0.1".equals(serverName)) {
                baseUrl = scheme + "://" + serverName + ":" + serverPort;
            } else {
                // 线上云托管，采用相对路径或云域名
                baseUrl = scheme + "://" + serverName;
            }

            String fileUrl = baseUrl + "/uploads/" + filename;

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            return ApiResponse.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("图片保存失败: " + e.getMessage());
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
