package com.tencent.wxcloudrun.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * index控制器
 */
@Controller

public class IndexController {

  /**
   * 主页页面
   * @return API response html
   */
  @GetMapping
  public String index() {
    return "index";
  }

  /**
   * 管理后台主页跳转映射
   * @return 重定向到不分离静态资源管理后台
   */
  @GetMapping("/admin")
  public String admin() {
    return "redirect:/admin/index.html";
  }

}
