package com.tencent.wxcloudrun.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * index控制器
 */
@Controller
public class IndexController {

  private static final Logger log = LoggerFactory.getLogger(IndexController.class);

  /**
   * 主页页面 (已改为当前的 CMS 管理后台登录主页)
   * @return API response html
   */
  @GetMapping
  public String index() {
    log.info("===> [juzishuoqiu CMS] 收到请求，正在加载并打印后端管理页面...");
    System.out.println("===> [juzishuoqiu CMS] 收到请求，正在加载并打印后端管理页面...");
    return "index";
  }

  /**
   * 管理后台主页跳转映射 (兼容跳转，统一路由至主页)
   * @return 重定向到不分离静态资源管理后台主页
   */
  @GetMapping("/admin")
  public String admin() {
    log.info("===> 收到旧路径 /admin 请求，重定向至管理登录页 /");
    return "redirect:/";
  }

}
