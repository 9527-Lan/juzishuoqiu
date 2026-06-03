package com.tencent.wxcloudrun.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class FileViewController {

    public static volatile String bucketHost = null;

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
     * 根据 query 参数中的 fileId 进行中转和 302 重定向
     */
    @GetMapping("/download")
    public void viewUploadFile(@RequestParam(value = "fileId", required = false) String fileId, HttpServletResponse response) {
        System.out.println("===> [图片访问] 触发 /download 接口，接收到文件ID: " + fileId);
        resolveAndRedirect(fileId, response);
    }

    /**
     * 兼容旧版原有的 /uploads/xxx.png 相对路径请求
     */
    @GetMapping("/uploads/**")
    public void viewUploadFileFromPath(HttpServletRequest request, HttpServletResponse response) {
        String requestURI = request.getRequestURI();
        System.out.println("===> [图片访问] 触发旧版相对路径 /uploads/** 接口，URI: " + requestURI);
        if (requestURI != null && requestURI.contains("/")) {
            String filename = requestURI.substring(requestURI.lastIndexOf("/") + 1);
            resolveAndRedirect(filename, response);
        }
    }

    private void resolveAndRedirect(String fileId, HttpServletResponse response) {
        System.out.println("===> [图片解析] 开始处理重定向流程. fileId=" + fileId + ", 当前缓存的存储桶域名=" + bucketHost);
        if (fileId == null || fileId.trim().isEmpty()) {
            System.err.println("===> [图片解析] 错误: 文件ID为空！");
            return;
        }

        String envId = System.getenv("CBR_ENV_ID");
        if (envId == null || envId.trim().isEmpty()) {
            envId = System.getenv("WX_ENV_ID");
        }
        if (envId == null || envId.trim().isEmpty()) {
            envId = "prod-d4gmc9oke67a3ac4e";
        }
        System.out.println("===> [图片解析] 解析得到云环境ID: " + envId);

        cn.hutool.json.JSONArray fileListArray = JSONUtil.createArray();

        if (fileId.startsWith("cloud://")) {
            System.out.println("===> [图片解析] 识别为完整微信云存储直链，直接使用: " + fileId);
            JSONObject f = JSONUtil.createObj().set("fileid", fileId).set("max_age", 7200);
            fileListArray.set(f);
        } else {
            System.out.println("===> [图片解析] 识别为旧版纯文件名，开始构造预测存储桶队列...");
            String storagePath = "uploads/" + fileId;

            if (bucketHost != null && !bucketHost.trim().isEmpty()) {
                JSONObject f1 = JSONUtil.createObj().set("fileid", "cloud://" + bucketHost + "/" + storagePath).set("max_age", 7200);
                fileListArray.set(f1);
            }
            JSONObject f2 = JSONUtil.createObj().set("fileid", "cloud://" + envId + ".7465-" + envId + "-1258717764/" + storagePath).set("max_age", 7200);
            fileListArray.set(f2);
            JSONObject f3 = JSONUtil.createObj().set("fileid", "cloud://" + envId + ".7465-" + envId + "/" + storagePath).set("max_age", 7200);
            fileListArray.set(f3);
            JSONObject f4 = JSONUtil.createObj().set("fileid", "cloud://" + envId + "/" + storagePath).set("max_age", 7200);
            fileListArray.set(f4);
        }

        System.out.println("===> [图片解析] 待测试解析的候选文件队列: " + fileListArray);

        try {
            String accessToken = getWxMaService().getAccessToken();
            System.out.println("===> [图片解析] 成功获取微信接口 AccessToken: " + (accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) + "..." : "空"));

            JSONObject requestBody = JSONUtil.createObj();
            requestBody.set("env", envId);
            requestBody.set("file_list", fileListArray);

            String requestUrl = "https://api.weixin.qq.com/tcb/batchdownloadfile?access_token=" + accessToken;
            String responseStr = HttpUtil.post(requestUrl, requestBody.toString());
            System.out.println("===> [图片解析] 微信服务器返回数据: " + responseStr);
            JSONObject responseJson = JSONUtil.parseObj(responseStr);

            if (responseJson != null && Integer.valueOf(0).equals(responseJson.getInt("errcode"))) {
                cn.hutool.json.JSONArray fileList = responseJson.getJSONArray("file_list");
                if (fileList != null) {
                    for (int i = 0; i < fileList.size(); i++) {
                        JSONObject fileResult = fileList.getJSONObject(i);
                        System.out.println("===> [图片解析] 候选对象 " + i + " 微信返回状态: " + fileResult);
                        if (Integer.valueOf(0).equals(fileResult.getInt("status"))) {
                            String downloadUrl = fileResult.getStr("download_url");
                            if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                                System.out.println("===> [图片解析] 成功获取下载签名直链！正在发起 302 重定向: " + downloadUrl);
                                response.sendRedirect(downloadUrl);
                                return;
                            }
                        }
                    }
                }
            }
            System.err.println("===> [图片解析] 错误: 所有预估候选文件 ID 均未能成功换取微信签名链接！");
            response.sendError(404, "File not found or signature generation failed");
        } catch (Exception e) {
            System.err.println("===> [图片解析] 微信换取下载直链发生异常:");
            e.printStackTrace();
            try {
                response.sendError(500, "Internal Server Error: " + e.getMessage());
            } catch (Exception ignored) {}
        }
    }
}
