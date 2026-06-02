package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("INSERT INTO `user_comment` (`user_id`, `article_id`, `content`, `status`, `parent_id`, `reply_to_user_nickname`) " +
            "VALUES (#{userId}, #{articleId}, #{content}, 1, #{parentId}, #{replyToUserNickname})") // status = 1: 直接发表
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT c.*, u.`nickname` as `user_nickname`, u.`avatar_url` as `user_avatar` " +
            "FROM `user_comment` c " +
            "LEFT JOIN `user` u ON c.`user_id` = u.`id` " +
            "WHERE c.`article_id` = #{articleId} AND c.`status` = 1 " +
            "ORDER BY c.`id` DESC")
    List<Comment> findByArticleId(@Param("articleId") Long articleId);

    @Select("<script>" +
            "SELECT c.*, u.`nickname` as `user_nickname`, u.`avatar_url` as `user_avatar`, " +
            "a.`title` as `article_title`, a.`thumbnail` as `article_thumbnail` " +
            "FROM `user_comment` c " +
            "LEFT JOIN `user` u ON c.`user_id` = u.`id` " +
            "LEFT JOIN `article` a ON c.`article_id` = a.`id` " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (c.`content` LIKE CONCAT('%', #{keyword}, '%') " +
            "  OR u.`nickname` LIKE CONCAT('%', #{keyword}, '%') " +
            "  OR a.`title` LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY c.`id` DESC" +
            "</script>")
    List<Comment> findAllForAdmin(@Param("keyword") String keyword);

    @Select("SELECT c.*, u.`nickname` as `user_nickname`, u.`avatar_url` as `user_avatar`, " +
            "a.`title` as `article_title`, a.`thumbnail` as `article_thumbnail` " +
            "FROM `user_comment` c " +
            "LEFT JOIN `user` u ON c.`user_id` = u.`id` " +
            "LEFT JOIN `article` a ON c.`article_id` = a.`id` " +
            "WHERE c.`user_id` = #{userId} " +
            "ORDER BY c.`id` DESC")
    List<Comment> findMyComments(@Param("userId") Long userId);

    @Delete("DELETE FROM `user_comment` WHERE `id` = #{id}")
    int deleteById(@Param("id") Long id);
}
