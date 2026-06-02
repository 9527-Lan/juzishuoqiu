package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.Article;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Select("<script>" +
            "SELECT * FROM `article` WHERE `type` = #{type} AND `status` = 1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND `title` LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY `publish_time` DESC, `id` DESC" +
            "</script>")
    List<Article> findPublishedByTypeAndKeyword(@Param("type") Integer type, @Param("keyword") String keyword);

    @Select("SELECT * FROM `article` WHERE `id` = #{id}")
    Article findById(@Param("id") Long id);

    @Update("UPDATE `article` SET `read_count` = `read_count` + 1 WHERE `id` = #{id}")
    int incrementReadCount(@Param("id") Long id);

    // 点赞判断及切换
    @Select("SELECT COUNT(1) FROM `user_like` WHERE `user_id` = #{userId} AND `article_id` = #{articleId}")
    int checkLikeExists(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Insert("INSERT INTO `user_like` (`user_id`, `article_id`) VALUES (#{userId}, #{articleId})")
    int insertLike(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Delete("DELETE FROM `user_like` WHERE `user_id` = #{userId} AND `article_id` = #{articleId}")
    int deleteLike(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Update("UPDATE `article` SET `like_count` = `like_count` + #{offset} WHERE `id` = #{articleId}")
    int offsetLikeCount(@Param("articleId") Long articleId, @Param("offset") int offset);

    // 收藏判断及切换
    @Select("SELECT COUNT(1) FROM `user_favorite` WHERE `user_id` = #{userId} AND `article_id` = #{articleId}")
    int checkFavoriteExists(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Insert("INSERT INTO `user_favorite` (`user_id`, `article_id`) VALUES (#{userId}, #{articleId})")
    int insertFavorite(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Delete("DELETE FROM `user_favorite` WHERE `user_id` = #{userId} AND `article_id` = #{articleId}")
    int deleteFavorite(@Param("userId") Long userId, @Param("articleId") Long articleId);

    // 查询指定赛事方案的收藏总人数
    @Select("SELECT COUNT(1) FROM `user_favorite` WHERE `article_id` = #{articleId}")
    int countFavoritesByArticleId(@Param("articleId") Long articleId);

    // 查询我的收藏文章列表
    @Select("SELECT * FROM `article` WHERE `id` IN (SELECT `article_id` FROM `user_favorite` WHERE `user_id` = #{userId}) AND `status` = 1 ORDER BY `id` DESC")
    List<Article> findMyFavorites(@Param("userId") Long userId);

    // 统计某个特定用户的收藏方案总数，供后台分页使用
    @Select("SELECT COUNT(1) FROM `user_favorite` WHERE `user_id` = #{userId}")
    int countFavoritesByUserId(@Param("userId") Long userId);

    // 分页查询某个特定用户的收藏方案列表，并带出其收藏的时间 f.create_time
    @Select("SELECT a.*, f.`create_time` as `favorite_time` " +
            "FROM `article` a " +
            "JOIN `user_favorite` f ON a.`id` = f.`article_id` " +
            "WHERE f.`user_id` = #{userId} " +
            "ORDER BY f.`id` DESC LIMIT #{limit} OFFSET #{offset}")
    List<java.util.Map<String, Object>> findFavoritesByUserIdWithPage(@Param("userId") Long userId,
                                                                      @Param("offset") int offset,
                                                                      @Param("limit") int limit);


    // ================== CMS 管理后台专区 ==================
    @Select("<script>" +
            "SELECT * FROM `article` WHERE 1=1 " +
            "<if test='type != null'>" +
            "  AND `type` = #{type} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (`title` LIKE CONCAT('%', #{keyword}, '%') " +
            "  OR `subtitle` LIKE CONCAT('%', #{keyword}, '%') " +
            "  OR `author` LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY `id` DESC" +
            "</script>")
    List<Article> findAllForAdmin(@Param("keyword") String keyword, @Param("type") Integer type);

    @Insert("INSERT INTO `article` (`title`, `subtitle`, `type`, `thumbnail`, `content`, `author`, `virtual_read`, `status`, `publish_time`) " +
            "VALUES (#{title}, #{subtitle}, #{type}, #{thumbnail}, #{content}, #{author}, #{virtualRead}, #{status}, #{publishTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Article article);

    @Update("UPDATE `article` SET `title` = #{title}, `subtitle` = #{subtitle}, `type` = #{type}, " +
            "`thumbnail` = #{thumbnail}, `content` = #{content}, `author` = #{author}, " +
            "`virtual_read` = #{virtualRead}, `status` = #{status} WHERE `id` = #{id}")
    int update(Article article);

    @Delete("DELETE FROM `article` WHERE `id` = #{id}")
    int deleteById(@Param("id") Long id);
}
