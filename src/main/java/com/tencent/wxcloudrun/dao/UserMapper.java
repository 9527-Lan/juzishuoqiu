package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE `openid` = #{openid} LIMIT 1")
    User findByOpenid(@Param("openid") String openid);

    @Select("SELECT * FROM `user` WHERE `nickname` = #{nickname} LIMIT 1")
    User findByNickname(@Param("nickname") String nickname);

    @Select("SELECT * FROM `user` WHERE `id` = #{id}")
    User findById(@Param("id") Long id);

    @Select("SELECT * FROM `user` ORDER BY `id` DESC")
    List<User> findAll();

    @Insert("INSERT INTO `user` (`openid`, `unionid`, `nickname`, `avatar_url`, `phone`, `status`, `role`) " +
            "VALUES (#{openid}, #{unionid}, #{nickname}, #{avatarUrl}, #{phone}, #{status}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE `user` SET `nickname` = #{nickname}, `avatar_url` = #{avatarUrl}, " +
            "`phone` = #{phone}, `status` = #{status}, `role` = #{role} WHERE `id` = #{id}")
    int update(User user);
}
