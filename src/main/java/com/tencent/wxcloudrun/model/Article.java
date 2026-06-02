package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.util.Date;

@Data
public class Article {
    private Long id;
    private String title;
    private String subtitle;
    private Integer type; // 1:推荐 2:今日方案 3:昨日回顾
    private String thumbnail;
    private String content;
    private String author;
    private Integer readCount;
    private Integer virtualRead;
    private Integer likeCount;
    private Integer status; // 0:草稿 1:已发布 2:下架
    private Date publishTime;
    private Date createTime;
    private Date updateTime;
}
