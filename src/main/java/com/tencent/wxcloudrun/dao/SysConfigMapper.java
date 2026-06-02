package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.SysConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysConfigMapper {

    @Select("SELECT `config_value` FROM `sys_config` WHERE `config_key` = #{key} LIMIT 1")
    String getValueByKey(@Param("key") String key);

    @Select("SELECT * FROM `sys_config` ORDER BY `id` ASC")
    List<SysConfig> findAll();

    @Update("INSERT INTO `sys_config` (`config_key`, `config_value`) VALUES (#{key}, #{value}) " +
            "ON DUPLICATE KEY UPDATE `config_value` = #{value}")
    int saveOrUpdate(@Param("key") String key, @Param("value") String value);
}
