package com.tencent.wxcloudrun.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    // 注册 Sa-Token 拦截器，打开注解式鉴权功能
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册路由拦截器，自适应拦截鉴权
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 拦截管理后台接口 (排除登录和公共图片上传接口)
            SaRouter.match("/api/v1/admin/**")
                    .notMatch("/api/v1/admin/login", "/api/v1/admin/upload")
                    .check(r -> StpUtil.checkLogin());

            // 拦截小程序敏感用户操作接口 (排除开放获取接口)
            SaRouter.match("/api/v1/mini/user/update-profile", 
                           "/api/v1/mini/article/like", 
                           "/api/v1/mini/article/favorite", 
                           "/api/v1/mini/article/comment",
                           "/api/v1/mini/article/my-favorites",
                           "/api/v1/mini/article/my-comments")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}
