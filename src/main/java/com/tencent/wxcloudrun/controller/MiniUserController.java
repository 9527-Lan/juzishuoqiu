package com.tencent.wxcloudrun.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.dao.UserMapper;
import com.tencent.wxcloudrun.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mini/user")
public class MiniUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.tencent.wxcloudrun.dao.SysConfigMapper sysConfigMapper;

    /**
     * 获取系统绑定的专属客服电话热线与微信客服信息
     */
    @GetMapping("/hotline")
    public ApiResponse getHotline() {
        String hotline = sysConfigMapper.getValueByKey("service_hotline");
        String corpId = sysConfigMapper.getValueByKey("service_corp_id");
        String chatUrl = sysConfigMapper.getValueByKey("service_chat_url");

        if (hotline == null || hotline.trim().isEmpty()) {
            hotline = "19232520317"; // 降级默认值
        }
        if (corpId == null || corpId.trim().isEmpty()) {
            corpId = "ww88c1c4f52bd328a6"; // 默认/降级企业ID
        }
        if (chatUrl == null || chatUrl.trim().isEmpty()) {
            chatUrl = "https://work.weixin.qq.com/kfid/kfc123456789abc"; // 默认/降级链接
        }

        Map<String, String> result = new HashMap<>();
        result.put("hotline", hotline);
        result.put("corpId", corpId);
        result.put("chatUrl", chatUrl);
        return ApiResponse.ok(result);
    }

    @Value("${wx.miniprogram.appid:wx8ac8fb0b363e193e}")
    private String wxAppid;

    @Value("${wx.miniprogram.secret:7ff2d70328143787f164868c87a9a12f}")
    private String wxSecret;

    /**
     * 自动微信授权快捷登录/注册
     * 微信云托管在 header 中会自动注入 X-WX-OPENID
     * 本地调试时，如果通过 wx.request 调用，可使用 mock 机制
     */
    @PostMapping("/login")
    public ApiResponse login(@RequestHeader(value = "X-WX-OPENID", required = false) String openid,
                             @RequestBody(required = false) Map<String, String> body) {

        String code = null;
        String nickname = null;
        String avatarUrl = null;

        if (body != null) {
            code = body.get("code");
            nickname = body.get("nickname");
            avatarUrl = body.get("avatarUrl");
        }

        // 1. 如果前端传来了 wx.login 换取的临时凭证 code，通过微信官方服务器换取真实 openid
        if (code != null && !code.trim().isEmpty() && wxSecret != null && !wxSecret.trim().isEmpty()) {
            try {
                String exchangeUrl = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxAppid +
                        "&secret=" + wxSecret + "&js_code=" + code + "&grant_type=authorization_code";

                RestTemplate restTemplate = new RestTemplate();
                String response = restTemplate.getForObject(exchangeUrl, String.class);

                if (response != null && response.contains("openid")) {
                    // 极简解析返回的 JSON，获取真实 openid
                    int start = response.indexOf("\"openid\":\"") + 10;
                    int end = response.indexOf("\"", start);
                    openid = response.substring(start, end);
                    System.out.println("===> [微信登录] Code换取 OpenId 成功: " + openid);
                }
            } catch (Exception e) {
                System.err.println("===> [微信登录] Code换取 OpenId 异常 (降级处理): " + e.getMessage());
            }
        }

        // 2. 兜底处理：如果是线上微信云托管，直接读取 X-WX-OPENID 请求头；如果是本地联调，支持 body 传参或兜底 mock
        if (openid == null || openid.isEmpty()) {
            if (body != null && body.containsKey("openid")) {
                openid = body.get("openid");
            } else {
                openid = "mock-openid-9527-test"; // 本地测试默认 OpenID
            }
        }

        User user = userMapper.findByOpenid(openid);

        if (user == null) {
            // 新增并初始化注册用户
            user = new User();
            user.setOpenid(openid);
            
            // 智能自动生成并不重复昵称的校验机制
            String finalNickname = nickname;
            if (finalNickname == null || finalNickname.trim().isEmpty() || "微信用户".equals(finalNickname)) {
                do {
                    int rand = (int) ((Math.random() * 900000) + 100000); // 六位随机数
                    finalNickname = "橘子球友_" + rand;
                } while (userMapper.findByNickname(finalNickname) != null);
            } else {
                if (userMapper.findByNickname(finalNickname) != null) {
                    String original = finalNickname;
                    do {
                        int rand = (int) ((Math.random() * 9000) + 1000); // 四位随机后缀
                        finalNickname = original + "_" + rand;
                    } while (userMapper.findByNickname(finalNickname) != null);
                }
            }
            user.setNickname(finalNickname);
            
            user.setAvatarUrl(avatarUrl != null && !avatarUrl.trim().isEmpty() ? avatarUrl : "https://api.dicebear.com/7.x/adventurer/svg?seed=Felix");
            user.setStatus(1);
            user.setRole("user");
            userMapper.insert(user);
        } else {
            // 已经存在的用户再次登录，不应该修改/覆盖已有的头像和昵称，保持已有资料不变
        }

        // 使用 Sa-Token 登录微信小程序用户
        StpUtil.login(user.getId());
        // 获取生成的 Token 字符串
        String tokenValue = StpUtil.getTokenValue();

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("token", tokenValue);

        return ApiResponse.ok(result);
    }

    /**
     * 修改用户昵称与头像
     */
    @PostMapping("/update-profile")
    public ApiResponse updateProfile(@RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        String avatarUrl = body.get("avatarUrl");

        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        User user = userMapper.findById(userId);
        if (user == null) {
            return ApiResponse.error("用户不存在，请先登录");
        }

        if (nickname != null && !nickname.trim().isEmpty()) {
            // 检查昵称唯一性（排除自己）
            User existing = userMapper.findByNickname(nickname.trim());
            if (existing != null && !existing.getId().equals(user.getId())) {
                return ApiResponse.error("该昵称已被其他球友占用，换一个试试吧");
            }
            user.setNickname(nickname.trim());
        }
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            user.setAvatarUrl(avatarUrl);
        }

        userMapper.update(user);

        // 统一返回 { user, token } 结构，确保小程序端登录凭证本地存储永远不会失效/丢失
        String tokenValue = cn.dev33.satoken.stp.StpUtil.getTokenValue();
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("user", user);
        result.put("token", tokenValue);

        return ApiResponse.ok(result);
    }
}
