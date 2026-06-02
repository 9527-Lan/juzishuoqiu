package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.util.Date;

@Data
public class User {
    private Long id;
    private String openid;
    private String unionid;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private Integer status;
    private String role;
    private Date createTime;
    private Date updateTime;
}
