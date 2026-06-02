# 「橘子说球」微信云托管前后端不分离项目产品与技术解决方案

根据微信小程序 **「橘子爱说球3」** 的实际交互特征，结合**微信云托管 (WeChat Cloud Run)** 容器化环境特性，本方案采用**前后端不分离（单容器一体化托管）**的系统架构。

本方案将**小程序后台业务 APIs** 和**运营管理后台 (CMS) 静态页面**合并打包部署在同一个 Spring Boot 容器中，对外统一监听 `80` 端口。运营人员通过访问容器服务的 `/admin` 路径即可进入管理后台，而小程序客户端则直接通过云托管免域名通道调用服务。

---

## 一、 系统架构设计 (前后端不分离)

### 1. 物理运行机制

```
                               ┌────────────────────────────────┐
                               │       微信小程序 客户端 (手机)   │
                               └───────────────┬────────────────┘
                                               │ (通过 wx.cloud.callContainer
                                               │  免域名、免SSL、免Token安全调用)
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                          微信云托管 (WeChat Cloud Run) 容器服务                            │
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │                            Spring Boot 单体应用 (Port: 80)                         │  │
│  │                                                                                   │  │
│  │  ┌─────────────────────────┐  ┌─────────────────────────┐  ┌───────────────────┐  │  │
│  │  │  前端管理后台 (CMS SPA)   │  │    Spring MVC 拦截器    │  │    业务逻辑 APIs  │  │  │
│  │  │                         │  │                         │  │                   │  │  │
│  │  │ 存放于:                 │  │ 1. 静态资源直放         │  │ 提供以下接口：     │  │  │
│  │  │ static/admin/index.html │  │ 2. /api/v1/admin 验权   │  │ - 方案列表及富文本 │  │  │
│  │  │ static/admin/js, css    │  │ 3. /api/v1/mini 开放调用│  │ - 收藏/点赞/评论  │  │  │
│  │  └────────────┬────────────┘  └────────────┬────────────┘  │ - 微信用户自动登录│  │  │
│  │               │                            │               └─────────┬─────────┘  │  │
│  │               └────────────────────────────┼─────────────────────────┘            │  │
│  │                                            ▼                                      │  │
│  │                                  MyBatis-Plus & JDBC                              │  │
│  └────────────────────────────────────────────┬──────────────────────────────────────┘  │
│                                               │                                         │
│                                               ▼                                         │
│                                      云托管内网 MySQL 8.0                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2. 目录结构与职责划分

```
juzishuoqiu (项目根目录)
├── Dockerfile                      # 容器构建声明（包含 Maven 打包与 Java 运行时环境）
├── pom.xml                         # Maven 依赖（引入 Spring Boot Web, MySQL, MyBatis）
├── src
│   └── main
│       ├── java
│       │   └── com.tencent.wxcloudrun
│       │       ├── WxCloudRunApplication.java   # Spring Boot 启动入口
│       │       ├── config          # 配置类（跨域配置、JSON格式化、微信合规拦截器）
│       │       ├── controller
│       │       │   ├── IndexController.java     # 基础跳转控制器
│       │       │   ├── MiniArticleController.java# 小程序端接口（方案展示、收藏、评论）
│       │       │   ├── MiniUserController.java   # 小程序端接口（快速登录、资料同步）
│       │       │   └── AdminCmsController.java  # 管理后台专用接口（发布方案、管理评论）
│       │       ├── dao             # 数据访问层
│       │       ├── model           # 数据实体层 (User, Article, Like, Favorite, Comment)
│       │       └── service         # 业务逻辑层
│       └── resources
│           ├── application.yml     # 配置文件（定义端口80、云托管数据库变量读取）
│           ├── db.sql              # 数据库初始化脚本（云托管部署时自动执行）
│           ├── mapper              # MyBatis XML 映射
│           └── static              # 【不分离核心】静态资源存放区
│               └── admin           # 管理后台前端构建包 (Vue/React 编译产物)
│                   ├── index.html  # 运营看板首页
│                   ├── css/
│                   └── js/
```

---

## 二、 数据库结构设计

数据库脚本已写入项目 `src/main/resources/db.sql` 中。表关系核心约束如下：

1.  **`user`（用户表）**：记录用户的微信 `openid`、昵称、头像。区分 `role` 字段（`admin` 与 `user`）。
2.  **`article`（方案文章表）**：包含 `type` 字段（`1:推荐`、`2:今日方案`、`3:昨日回顾`），支持富文本（`content`）存储赛事分析，并有 `virtual_read`（虚拟阅读量，用于前期运营。
3.  **`user_comment`（评论表）**：记录用户发表的互动言论，在言论落库前强制调用微信内容安全接口（`msgSecCheck`）进行自动合规检测。同时在后台管理端提供一键删除功能，保障内容安全。

---

## 三、 核心代码框架实现

### 1. Spring Boot 基础环境配置 (`application.yml`)
在微信云托管中，无需硬编码数据库 IP 和密码，容器启动时微信会自动注入环境变量。
```yaml
server:
  port: 80                         # 微信云托管容器必须监听 80 端口

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 通过系统环境变量自动注入云数据库连接参数
    url: jdbc:mysql://${MYSQL_ADDRESS}/${MYSQL_DATABASE:springboot_demo}?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
  mvc:
    static-path-pattern: /**       # 静态资源路由映射
```

### 2. 小程序客户端数据获取 API (`MiniArticleController.java`)
小程序端需要获取推荐、今日方案、昨日回顾列表，并查看方案详情。

```java
package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.model.Article;
import com.tencent.wxcloudrun.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mini/article")
public class MiniArticleController {

    @Autowired
    private ArticleService articleService;

    /**
     * 根据栏目分类分页查询方案列表
     * @param type 1:推荐 2:今日方案 3:昨日回顾
     */
    @GetMapping("/list")
    public ApiResponse getArticleList(@RequestParam("type") Integer type,
                                      @RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                      @RequestParam(value = "size", defaultValue = "10") int size) {
        List<Article> list = articleService.getArticlePage(type, keyword, page, size);
        return ApiResponse.ok(list);
    }

    /**
     * 获取方案详情（每次调用自动累加真实阅读量）
     */
    @GetMapping("/detail/{id}")
    public ApiResponse getArticleDetail(@PathVariable("id") Long id, 
                                        @RequestHeader(value = "X-WX-OPENID", required = false) String openid) {
        Article article = articleService.getArticleDetailAndIncrementRead(id, openid);
        if (article == null) {
            return ApiResponse.error("方案不存在或已被下架");
        }
        return ApiResponse.ok(article);
    }
    
    /**
     * 用户收藏/取消收藏
     */
    @PostMapping("/favorite")
    public ApiResponse toggleFavorite(@RequestParam("articleId") Long articleId,
                                      @RequestHeader("X-WX-OPENID") String openid) {
        boolean status = articleService.toggleFavorite(articleId, openid);
        return ApiResponse.ok(status);
    }

    /**
     * 用户点赞/取消点赞
     */
    @PostMapping("/like")
    public ApiResponse toggleLike(@RequestParam("articleId") Long articleId,
                                  @RequestHeader("X-WX-OPENID") String openid) {
        boolean status = articleService.toggleLike(articleId, openid);
        return ApiResponse.ok(status);
    }

    /**
     * 用户发表评论（在后端自动调用微信 msgSecCheck 敏感词安全检查）
     */
    @PostMapping("/comment")
    public ApiResponse addComment(@RequestParam("articleId") Long articleId,
                                  @RequestParam("content") String content,
                                  @RequestHeader("X-WX-OPENID") String openid) {
        // 1. 调用微信合规检测接口，防止出现博彩、违规等内容
        boolean isSafe = articleService.checkContentSecurity(content);
        if (!isSafe) {
            return ApiResponse.error("评论包含敏感不合规词汇，发布失败");
        }
        
        // 2. 检测安全后直接存入数据库展示
        articleService.saveComment(articleId, content, openid);
        return ApiResponse.ok("评论发表成功");
    }
}
```

### 3. 前后端不分离：单体静态资源路由与页面映射 (`IndexController.java`)
本类负责处理将 `/admin` 的前端路由与 Spring Boot 静态资源打通。运营人员直接访问 `http://<云托管域名>/admin/` 即可加载存在 `static/admin/index.html` 中的单页后台管理应用 (SPA)。

```java
package com.tencent.wxcloudrun.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    /**
     * 管理后台主页跳转映射
     */
    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/index.html";
    }

    /**
     * 小程序在 H5 端或浏览器端打开的落地欢迎页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
```

---

## 四、 运营管理后台 (CMS) 不分离版设计

由于采用**前后端不分离**的单容器部署模式，管理后台被完全打包在 Spring Boot 的静态资源目录中，并与后端路由无缝打通。

### 1. 静态资源结构
管理后台的所有前端界面和交互逻辑集成在一个高度优化的单页应用（SPA）中：
*   **入口路径**：`/admin`（由 `IndexController` 重定向至 `/admin/index.html`）。
*   **文件位置**：`src/main/resources/static/admin/index.html`。
*   **资源加载**：通过公共高速 CDN 异步载入 **Vue 3**、**Element Plus** 和 **Tailwind CSS**，无需任何本地 node 构建步骤（如 `npm run build`），实现了镜像体积轻量化与极速部署。

### 2. 核心功能板块
*   **安全认证登录锁**：
    *   管理后台配备单独的登录验证（默认账号：`admin`，默认密码：`orange888`，密码由数据库 `sys_config` 表读取）。
*   **控制台仪表盘 (Dashboard)**：
    *   展示全站运营核心指标：赛事研判方案总数、小程序注册用户数、累计评论总数。
    *   展示微信云托管的系统状态看板（包括数据库连通性、免签 callContainer 隧道与合规 msgSecCheck 检查器的状态）。
*   **赛事研判发布管理 (Articles)**：
    *   **发布表单项**：标题、发布分类（推荐、今日方案、昨日回顾）、次级高亮角标（如 `今日早场`）、封面图 URL（支持一键生成随机精美体育背景图）、虚拟/真实阅读量及点赞配置、研判解析富文本正文。
*   **用户评论管理 (Comments)**：
    *   列表形式展示全站用户发表的评论，显示评论者微信头像、昵称、发表时间、评论文本内容以及关联的目标方案文章。
    *   提供【删除】按钮的一键下架/物理删除操作，管理员可以随时清理不当或者违规言论。
*   **全局参数配置 (Configs)**：
    *   支持在线一键修改专属客服热线电话（原：`19232520317`）与管理员登录密码，保存直接实时落库同步。

### 3. 本地调测与沙箱离线降级机制
为了方便开发人员与运营人员在本地进行直观效果调测，即使未连通真实后端服务，前端管理页面也内置了**全套 LocalStorage 离线模拟数据和 Mock APIs 降级机制**：
*   在没有启动后端 API 服务，或接口请求遇到 404/500 报错时，前端将自动接管并降级为“本地测试沙箱模式”。
*   支持离线添加/修改方案、更新发布状态、删除不良评论及修改系统电话，修改后的数据会自动持久化到浏览器的 `localStorage` 中。

---

## 五、 微信小程序客户端 (前端) 接入方案

在微信云托管环境下，微信小程序前端**无需配置服务器域名 (Request Domain)**，也不需要处理繁琐的 HTTPS 证书、用户登录态 Token 校验。通过使用微信开放平台提供的官方免签 SDK，可以直接安全地与部署在云托管内的 Spring Boot 进行通信：

### 1. 初始化云环境 (App.js)
```javascript
App({
  onLaunch: function () {
    if (!wx.cloud) {
      console.error('请使用 2.2.3 或以上的基础库以使用云能力');
    } else {
      wx.cloud.init({
        env: 'prod-d4gmc9oke67a3ac4e', // 填写微信云托管的运行环境ID
        traceUser: true,
      });
    }
  }
});
```

### 2. 小程序前端安全调用 APIs 示例
通过 `wx.cloud.callContainer` 访问 Spring Boot 的接口：

```javascript
// 获取「今日方案」栏目列表数据
wx.cloud.callContainer({
  config: {
    env: 'prod-d4gmc9oke67a3ac4e', // 微信云托管环境ID
  },
  path: '/api/v1/mini/article/list',
  method: 'GET',
  data: {
    type: 2,        // 今日方案
    page: 1,
    size: 10
  },
  header: {
    'X-WX-SERVICE': 'springboot-drgz', // 云托管服务名称
  },
  success: (res) => {
    console.log('获取方案列表成功:', res.data);
    this.setData({
      schemes: res.data.data
    });
  },
  fail: (err) => {
    console.error('获取方案列表失败:', err);
  }
});
```

---

## 六、 微信平台敏感类目与内容安全合规指导

为防止小程序因为“提供彩票走势、非法博彩推荐、赛事预测”等涉赌涉规行为被平台下架，必须在内容管理 and 文案描述上遵守以下核心准则：

### 1. 资质合规
*   **主体资质**：建议使用企业/自媒体资质申请小程序。
*   **选择服务类目**：选择 **「体育 > 体育资讯」**、**「工具 > 工具资讯」** 或 **「社交 > 社区/论坛」**。绝对不要选择与博彩、彩票相关的类目。

### 2. 界面与文案去博彩化 (敏感词过滤规避)
在前台方案分析、历史回顾文案中，必须严格执行**去博彩化**，用正规的“体育赛事学术 and 历史数据复盘分析”代替：

| 禁用博彩敏感词 | 推荐使用的替代正规词 |
|:---|:---|
| 胜负预测 / 推荐 / 稳胆 | 赛事深度研判 / 历史交锋复盘 / 实力对比 |
| 盘口 / 盘路 | 机构走势特征 / 历史数据趋势 |
| 水位 / 赔率 / 独赢 | 数据期望值 / 偏好特征指数 |
| 赌球 / 彩民 / 庄家 | 体育爱好者 / 主流分析模型 |
| 必中 / 100% 稳 / 胜率 | 数据支持率 / 概率模型分布 / 主场优势系数 |

### 3. 内容安全检测集成 (`msgSecCheck`)
当用户在小程序端修改头像昵称、发表方案评论时，必须调用微信的官方合规接口：
*   **头像/昵称拦截**：通过 `sys_config` 表 and 微信提供的敏感词检查，直接拦截非法、色情、涉赌的昵称。
*   **评论拦截**：在 Spring Boot 后端中引入微信 `security.msgSecCheck` 开放接口，对评论文本进行实时检测，发现敏感词直接返回“评论不合规，发表失败”提示。

---

## 七、 部署发布流程

1.  **管理后台前端编译打包**：
    *   在本地将运营管理后台的前端项目（如 Vue SPA）进行生产环境编译（`npm run build`）。
    *   将生成的 `dist` 目录下的所有静态资源（`index.html`、`static/` 目录等）复制并覆盖到本项目 `src/main/resources/static/admin/` 路径下。
2.  **代码提交与部署**：
    *   使用 `git commit` 将代码库（包含 Java 源码、`db.sql`、编译后的前端页面）提交至 GitHub。
    *   登录 **[微信云托管控制台]**，创建/选择服务 `springboot-drgz`。
    *   在“部署发布”中绑定 GitHub 仓库，选择 `main` 分支一键触发流水线构建。微信云托管将根据项目根目录下的 `Dockerfile` 自动拉取环境、编译 Java 项目并完成镜像部署。
3.  **内网数据库执行**：
    *   部署完成后，在云托管控制台“数据库”模块，将 `db.sql` 文件直接导入云数据库中执行，完成初始表结构以及默认管理员、专属客服热线的配置。
