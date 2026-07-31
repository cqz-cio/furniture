-- Tenant-scoped website inquiry mail relay configuration and delivery state.
-- SMTP credentials and the global system mail account remain platform-managed;
-- tenants only manage the receiving address and the inquiry mail presentation.

CREATE TABLE `system_website_inquiry_mail_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置编号',
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用询盘邮件转发',
  `recipient_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '' COMMENT '接收询盘的内部邮箱',
  `mail_account_id` bigint DEFAULT NULL COMMENT '平台邮箱账号编号',
  `mail_template_id` bigint DEFAULT NULL COMMENT '同步生成的系统邮件模板编号',
  `sender_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'VANZ Inquiry Desk' COMMENT '发件人显示名称',
  `subject_template` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL COMMENT '询盘邮件标题模板',
  `content_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL COMMENT '询盘邮件 HTML 模板',
  `erp_base_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '' COMMENT 'ERP 管理端访问地址',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_inquiry_mail_config_tenant`
    (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='官网询盘邮件转发配置';

CREATE TABLE `system_website_inquiry_mail_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '投递编号',
  `inquiry_id` bigint NOT NULL COMMENT 'CRM 询盘编号',
  `external_inquiry_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL COMMENT '官网询盘幂等编号',
  `recipient_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '' COMMENT '内部接收邮箱快照',
  `customer_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '' COMMENT '客户回复邮箱快照',
  `status` tinyint NOT NULL DEFAULT 0
    COMMENT '投递状态：0待发送，10发送中，20成功，30失败，40待配置',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '发送尝试次数',
  `mail_log_id` bigint DEFAULT NULL COMMENT '系统邮件日志编号',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `sent_time` datetime DEFAULT NULL COMMENT '发送成功时间',
  `last_error` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT '' COMMENT '最近一次失败原因',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_inquiry_mail_delivery_inquiry`
    (`tenant_id`, `inquiry_id`, `deleted`),
  KEY `idx_website_inquiry_mail_delivery_retry`
    (`tenant_id`, `status`, `next_retry_time`, `attempt_count`, `deleted`),
  KEY `idx_website_inquiry_mail_delivery_log`
    (`mail_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='官网询盘邮件投递记录';
