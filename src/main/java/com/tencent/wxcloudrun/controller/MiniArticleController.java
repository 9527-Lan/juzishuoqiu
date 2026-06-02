package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.dao.ArticleMapper;
import com.tencent.wxcloudrun.dao.CommentMapper;
import com.tencent.wxcloudrun.model.Article;
import com.tencent.wxcloudrun.model.Comment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mini/article")
public class MiniArticleController {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private javax.sql.DataSource dataSource;

    @javax.annotation.PostConstruct
    public void initDatabaseSchema() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            
            // 自愈检测 1: 自动增加 parent_id 关联列
            try {
                stmt.execute("ALTER TABLE `user_comment` ADD COLUMN `parent_id` bigint DEFAULT NULL COMMENT '父评论ID'");
                System.out.println("===> [数据自愈引擎] 自动在 user_comment 表中新增 parent_id 列成功！");
            } catch (java.sql.SQLException e) {
                // 已存在或异常，静默处理
            }

            // 自愈检测 2: 自动增加 reply_to_user_nickname 列
            try {
                stmt.execute("ALTER TABLE `user_comment` ADD COLUMN `reply_to_user_nickname` varchar(100) DEFAULT NULL COMMENT '被回复者昵称'");
                System.out.println("===> [数据自愈引擎] 自动在 user_comment 表中新增 reply_to_user_nickname 列成功！");
            } catch (java.sql.SQLException e) {
                // 忽略已有列异常
            }

        } catch (Exception e) {
            System.err.println("===> [数据自愈引擎] 执行数据库升级失败: " + e.getMessage());
        }
    }

    /**
     * 获取文章方案列表
     */
    @GetMapping("/list")
    public ApiResponse getList(@RequestParam("type") Integer type,
                               @RequestParam(value = "keyword", required = false) String keyword) {
        List<Article> list = articleMapper.findPublishedByTypeAndKeyword(type, keyword);
        return ApiResponse.ok(list);
    }

    /**
     * 获取方案详情（自动增加阅读数，并获取点赞/收藏状态）
     */
    @GetMapping("/detail/{id}")
    public ApiResponse getDetail(@PathVariable("id") Long id,
                                 @RequestHeader(value = "X-WX-OPENID", required = false) String openid,
                                 @RequestParam(value = "openid", required = false) String paramOpenid) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            return ApiResponse.error("该赛事方案不存在或已被下架");
        }

        // 累加阅读数并执行非空安全判断，防止自动拆箱抛出 NullPointerException
        articleMapper.incrementReadCount(id);
        Integer currentRead = article.getReadCount();
        article.setReadCount(currentRead == null ? 1 : currentRead + 1);

        // 获取点赞、收藏状态 (摒弃 openid 传参，改由标准 Sa-Token 登录态动态获取用户 ID)
        boolean isLiked = false;
        boolean isFavorited = false;
        if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
            Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
            isLiked = articleMapper.checkLikeExists(userId, id) > 0;
            isFavorited = articleMapper.checkFavoriteExists(userId, id) > 0;
        }

        int favoriteCount = articleMapper.countFavoritesByArticleId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("article", article);
        result.put("isLiked", isLiked);
        result.put("isFavorited", isFavorited);
        result.put("favoriteCount", favoriteCount);

        return ApiResponse.ok(result);
    }

    /**
     * 切换点赞状态
     */
    @PostMapping("/like")
    public ApiResponse toggleLike(@RequestBody Map<String, Object> body) {
        Long articleId = Long.valueOf(body.get("articleId").toString());
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();

        boolean exists = articleMapper.checkLikeExists(userId, articleId) > 0;
        boolean nowLiked;
        if (exists) {
            articleMapper.deleteLike(userId, articleId);
            articleMapper.offsetLikeCount(articleId, -1);
            nowLiked = false;
        } else {
            articleMapper.insertLike(userId, articleId);
            articleMapper.offsetLikeCount(articleId, 1);
            nowLiked = true;
        }

        return ApiResponse.ok(nowLiked);
    }

    /**
     * 切换收藏状态
     */
    @PostMapping("/favorite")
    public ApiResponse toggleFavorite(@RequestBody Map<String, Object> body) {
        Long articleId = Long.valueOf(body.get("articleId").toString());
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();

        boolean exists = articleMapper.checkFavoriteExists(userId, articleId) > 0;
        boolean nowFavorited;
        if (exists) {
            articleMapper.deleteFavorite(userId, articleId);
            nowFavorited = false;
        } else {
            articleMapper.insertFavorite(userId, articleId);
            nowFavorited = true;
        }

        return ApiResponse.ok(nowFavorited);
    }

    /**
     * 发表评论
     */
    @PostMapping("/comment")
    public ApiResponse addComment(@RequestBody Map<String, Object> body) {
        Long articleId = Long.valueOf(body.get("articleId").toString());
        String content = (String) body.get("content");

        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.error("评论内容不能为空");
        }

        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();

        // 模拟敏感词校验机制 (也可调用微信官方 security.msgSecCheck)
        if (content.contains("博彩") || content.contains("稳赢") || content.contains("外围") || content.contains("赌球")) {
            return ApiResponse.error("评论包含不安全合规敏感词汇，发布失败！");
        }

        Long parentId = body.containsKey("parentId") && body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        String replyToUserNickname = (String) body.get("replyToUserNickname");

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setArticleId(articleId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setReplyToUserNickname(replyToUserNickname);
        commentMapper.insert(comment);

        return ApiResponse.ok(comment);
    }

    /**
     * 获取指定方案的发表评论列表
     */
    @GetMapping("/comments/{articleId}")
    public ApiResponse getComments(@PathVariable("articleId") Long articleId) {
        List<Comment> comments = commentMapper.findByArticleId(articleId);
        return ApiResponse.ok(comments);
    }

    /**
     * 获取当前登录用户的我的收藏方案列表
     */
    @GetMapping("/my-favorites")
    public ApiResponse getMyFavorites() {
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        List<Article> list = articleMapper.findMyFavorites(userId);
        return ApiResponse.ok(list);
    }

    /**
     * 获取当前登录用户的历史评论记录列表
     */
    @GetMapping("/my-comments")
    public ApiResponse getMyComments() {
        Long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        List<Comment> list = commentMapper.findMyComments(userId);
        return ApiResponse.ok(list);
    }
}
