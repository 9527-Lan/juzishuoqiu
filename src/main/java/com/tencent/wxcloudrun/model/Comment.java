package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.util.Date;

@Data
public class Comment {
    private Long id;
    private Long userId;
    private Long articleId;
    private String content;
    private Integer status; // 0:待审核 1:已发布 2:审核拒绝 (默认在不审核下我们全设为1)
    private String refuseReason;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // 新增回复体系字段
    private Long parentId;
    private String replyToUserNickname;

    // 关联辅助显示字段 (用于管理后台及联合查询)
    private String userNickname;
    private String userAvatar;
    private String articleTitle;
    private String articleThumbnail;
}
