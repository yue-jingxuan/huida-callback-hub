-- ============================================================
-- 汇答投流回调网关（huida-callback-hub）建表脚本
-- 数据库：huida_callback_hub  字符集：utf8mb4
-- ============================================================

-- 1. 回调日志表
CREATE TABLE IF NOT EXISTS `callback_log` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `platform`       varchar(20) NOT NULL COMMENT '平台:douyin/kuaishou/chuanshanjia',
  `event_id`       varchar(128) DEFAULT NULL COMMENT '事件唯一ID(用于幂等)',
  `mode_type`      tinyint     NOT NULL DEFAULT 1 COMMENT '1-CAPI出站上报 2-Webhook入站转发',
  `click_id`       varchar(100) DEFAULT NULL COMMENT '广告点击ID',
  `raw_body`       text        COMMENT '原始请求报文',
  `capi_request`   text        COMMENT 'CAPI上报请求报文(模式1)',
  `capi_response`  text        COMMENT 'CAPI上报响应报文(模式1)',
  `status`         varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending待处理,success成功,failed失败,dead死信',
  `retry_count`    int         NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `error_msg`      varchar(500) DEFAULT NULL COMMENT '失败错误信息',
  `trace_id`       varchar(50)  DEFAULT NULL COMMENT '链路追踪ID',
  `target_url`     varchar(255) DEFAULT NULL COMMENT '下游转发地址(模式2)',
  `created_at`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_click_id` (`click_id`),
  KEY `idx_status_retry` (`status`, `retry_count`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_mode_status` (`mode_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回调请求日志';

-- 2. 平台配置表
CREATE TABLE IF NOT EXISTS `platform_config` (
  `id`             bigint      NOT NULL AUTO_INCREMENT,
  `platform_code`  varchar(20) NOT NULL COMMENT '平台编码 douyin',
  `platform_name`  varchar(50) DEFAULT NULL COMMENT '平台名称',
  `config_json`    json        DEFAULT NULL COMMENT '平台配置: secret,token,api_url,超时等',
  `target_url`     varchar(255) DEFAULT NULL COMMENT '默认下游转发地址(模式2)',
  `webhook_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启Webhook接收 1开启 0关闭(默认)',
  `enabled`        tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0禁用',
  `remark`         varchar(500) DEFAULT NULL,
  `created_at`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_code` (`platform_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投流平台配置';
