-- 创建库
create database if not exists ai_passage_creator;
use ai_passage_creator;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
    ) comment '用户' collate = utf8mb4_unicode_ci;

-- 初始化测试数据（密码是 12345678，MD5 加密 + 盐值 yupi）
INSERT INTO user (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole) VALUES
                                                                                                  (1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员', 'https://www.codefather.cn/logo.png', '系统管理员', 'admin'),
                                                                                                  (2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户', 'https://www.codefather.cn/logo.png', '我是一个普通用户', 'user'),
                                                                                                  (3, 'test', '10670d38ec32fa8102be6a37f8cb52bf', '测试账号', 'https://www.codefather.cn/logo.png', '这是一个测试账号', 'user');
ALTER TABLE user ADD COLUMN userEmail VARCHAR(128) NULL COMMENT '用户邮箱' AFTER userAccount;
ALTER TABLE user ADD UNIQUE INDEX idx_user_email (userEmail);

-- 文章表
create table if not exists article
(
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64)                        not null comment '任务ID（UUID）',
    userId          bigint                             not null comment '用户ID',
    topic           varchar(500)                       not null comment '选题',
    mainTitle       varchar(200)                       null comment '主标题',
    subTitle        varchar(300)                       null comment '副标题',
    outline         json                               null comment '大纲（JSON格式）',
    content         text                               null comment '正文（Markdown格式）',
    fullContent     text                               null comment '完整图文（Markdown格式，含配图）',
    coverImage      varchar(512)                       null comment '封面图 URL',
    images          json                               null comment '配图列表（JSON数组）',
    status          varchar(20) default 'PENDING'      not null comment '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    errorMessage    text                               null comment '错误信息',
    createTime      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    completedTime   datetime                           null comment '完成时间',
    updateTime      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint     default 0              not null comment '是否删除',
    UNIQUE KEY uk_taskId (taskId),
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime),
    INDEX idx_userId_status (userId, status)
) comment '文章表' collate = utf8mb4_unicode_ci;

use ai_passage_creator;

-- 为 article 表添加阶段相关字段
ALTER TABLE article
    ADD COLUMN phase VARCHAR(50) DEFAULT 'PENDING' COMMENT '当前阶段：PENDING/TITLE_GENERATING/TITLE_SELECTING/OUTLINE_GENERATING/OUTLINE_EDITING/CONTENT_GENERATING' AFTER status,
    ADD COLUMN titleOptions JSON NULL COMMENT '标题方案列表（3-5个方案）' AFTER subTitle,
    ADD COLUMN userDescription TEXT NULL COMMENT '用户补充描述' AFTER topic,
    ADD COLUMN enabledImageMethods JSON NULL COMMENT '允许的配图方式列表' AFTER userDescription;

-- 为 article 表添加 style 字段（文章风格）
ALTER TABLE article
    ADD COLUMN style VARCHAR(20) NULL COMMENT '文章风格：tech/emotional/educational/humorous' AFTER topic;

-- 添加 quota 字段
ALTER TABLE user ADD COLUMN quota int default 5 not null comment '剩余配额' AFTER userRole;

-- 为已有用户设置默认配额
UPDATE user SET quota = 5 WHERE quota IS NULL;

-- 添加会员和支付功能
-- @author <a href="https://codefather.cn">编程导航学习圈</a>

-- 1. 扩展 user 表，添加会员相关字段
ALTER TABLE user
    ADD COLUMN vipTime DATETIME NULL COMMENT '成为会员时间';

-- 2. 创建支付记录表
CREATE TABLE IF NOT EXISTS payment_record (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                                              userId BIGINT NOT NULL COMMENT '用户ID',
                                              stripeSessionId VARCHAR(128) COMMENT 'Stripe Checkout Session ID',
                                              stripePaymentIntentId VARCHAR(128) COMMENT 'Stripe 支付意向ID',
                                              amount DECIMAL(10,2) NOT NULL COMMENT '金额（美元）',
                                              currency VARCHAR(8) DEFAULT 'usd' COMMENT '货币',
                                              status VARCHAR(32) NOT NULL COMMENT '状态：PENDING/SUCCEEDED/FAILED/REFUNDED',
                                              productType VARCHAR(32) NOT NULL COMMENT '产品类型：VIP_PERMANENT',
                                              description VARCHAR(256) COMMENT '描述',
                                              refundTime DATETIME NULL COMMENT '退款时间',
                                              refundReason VARCHAR(512) NULL COMMENT '退款原因',
                                              createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                              INDEX idx_userId (userId),
                                              INDEX idx_stripeSessionId (stripeSessionId),
                                              INDEX idx_status (status),
                                              INDEX idx_createTime (createTime)
) COMMENT '支付记录表' COLLATE = utf8mb4_unicode_ci;

-- 智能体执行日志表
create table if not exists agent_log
(
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64)                        not null comment '任务ID',
    agentName       varchar(50)                        not null comment '智能体名称',
    startTime       datetime                           not null comment '开始时间',
    endTime         datetime                           null comment '结束时间',
    durationMs      int                                null comment '耗时（毫秒）',
    status          varchar(20)                        not null comment '状态：SUCCESS/FAILED',
    errorMessage    text                               null comment '错误信息',
    prompt          text                               null comment '使用的Prompt',
    inputData       json                               null comment '输入数据（JSON格式）',
    outputData      json                               null comment '输出数据（JSON格式）',
    createTime      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint     default 0              not null comment '是否删除',
    INDEX idx_taskId (taskId),
    INDEX idx_agentName (agentName),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
) comment '智能体执行日志表' collate = utf8mb4_unicode_ci;