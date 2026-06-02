-- ============================================================
-- 「橘子说球」小程序前后端不分离版本数据库初始化脚本
-- 目标库：springboot_demo 或 微信云托管内 MySQL 库
-- ============================================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` varchar(64) NOT NULL COMMENT '微信OpenID',
  `unionid` varchar(64) DEFAULT NULL COMMENT '微信UnionID',
  `nickname` varchar(50) DEFAULT '微信用户' COMMENT '昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '专属热线',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `role` varchar(20) DEFAULT 'user' COMMENT '角色 user:普通用户 admin:管理员',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息表';

-- 2. 赛事方案/资讯表
CREATE TABLE IF NOT EXISTS `article` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(150) NOT NULL COMMENT '方案标题',
  `subtitle` varchar(100) DEFAULT NULL COMMENT '次级描述（今日方案/昨日回顾）',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型 1:推荐 2:今日方案 3:昨日回顾',
  `thumbnail` varchar(255) DEFAULT NULL COMMENT '列表缩略图',
  `content` text NOT NULL COMMENT '研判、赛事分析等富文本/结构化文字内容',
  `author` varchar(50) DEFAULT '橘子体育' COMMENT '发布人/作者',
  `read_count` int DEFAULT '0' COMMENT '真实阅读数',
  `virtual_read` int DEFAULT '0' COMMENT '虚拟阅读数（运营显示数 = 真实+虚拟）',
  `like_count` int DEFAULT '0' COMMENT '点赞数',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:草稿 1:已发布 2:下架',
  `publish_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事方案分析表';

-- 3. 用户收藏表
CREATE TABLE IF NOT EXISTS `user_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` bigint NOT NULL COMMENT '方案ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_article` (`user_id`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏夹';

-- 4. 用户点赞表
CREATE TABLE IF NOT EXISTS `user_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` bigint NOT NULL COMMENT '方案ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_article` (`user_id`,`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户点赞记录';

-- 5. 用户评论表 (先审后发，保障小程序内容安全合规)
CREATE TABLE IF NOT EXISTS `user_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` bigint NOT NULL COMMENT '方案ID',
  `content` text NOT NULL COMMENT '评论内容',
  `status` tinyint DEFAULT '0' COMMENT '审核状态 0:待审核 1:已审核发布 2:审核拒绝',
  `refuse_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_status` (`article_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户评论表';

-- 6. 全局/系统配置表 (如热线电话、微信客服配置、管理员密码)
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(50) NOT NULL COMMENT '配置键',
  `config_value` varchar(255) NOT NULL COMMENT '配置值',
  `config_desc` varchar(100) DEFAULT NULL COMMENT '配置描述',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 7. 预置基础配置数据
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_desc`) VALUES
('service_hotline', '19232520317', '专属客服电话'),
('admin_password', 'orange888', '管理后台管理员默认登录密码')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);