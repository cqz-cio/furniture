-- Enable the complete Yudao CRM module for the VANZ tenant.
-- The public Yudao distribution contains the CRM Java/Vue sources and menu
-- records, but does not ship the CRM business-table DDL. These tables mirror
-- the data objects in yudao-module-crm and keep the standard audit/tenant
-- columns used by the Oakved monolith.

CREATE TABLE IF NOT EXISTS `crm_clue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '线索编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '线索名称',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已跟进',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(512) DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `transform_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已转为客户',
  `customer_id` bigint DEFAULT NULL COMMENT '转换后的客户编号',
  `mobile` varchar(32) NOT NULL DEFAULT '' COMMENT '手机号',
  `telephone` varchar(32) NOT NULL DEFAULT '' COMMENT '电话',
  `qq` varchar(64) NOT NULL DEFAULT '' COMMENT 'QQ',
  `wechat` varchar(255) NOT NULL DEFAULT '' COMMENT '微信',
  `email` varchar(255) NOT NULL DEFAULT '' COMMENT '邮箱',
  `area_id` int DEFAULT NULL COMMENT '地区编号',
  `detail_address` varchar(255) NOT NULL DEFAULT '' COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '客户行业',
  `level` int DEFAULT NULL COMMENT '客户级别',
  `source` int DEFAULT NULL COMMENT '客户来源',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_clue_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`),
  KEY `idx_crm_clue_tenant_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_clue_tenant_create_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 线索';

CREATE TABLE IF NOT EXISTS `crm_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '客户名称',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已跟进',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(512) DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `owner_time` datetime DEFAULT NULL COMMENT '成为负责人的时间',
  `lock_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否锁定',
  `deal_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否成交',
  `mobile` varchar(32) NOT NULL DEFAULT '' COMMENT '手机号',
  `telephone` varchar(32) NOT NULL DEFAULT '' COMMENT '电话',
  `qq` varchar(64) NOT NULL DEFAULT '' COMMENT 'QQ',
  `wechat` varchar(255) NOT NULL DEFAULT '' COMMENT '微信',
  `email` varchar(255) NOT NULL DEFAULT '' COMMENT '邮箱',
  `area_id` int DEFAULT NULL COMMENT '地区编号',
  `detail_address` varchar(255) NOT NULL DEFAULT '' COMMENT '详细地址',
  `industry_id` int DEFAULT NULL COMMENT '客户行业',
  `level` int DEFAULT NULL COMMENT '客户级别',
  `source` int DEFAULT NULL COMMENT '客户来源',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_customer_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`),
  KEY `idx_crm_customer_tenant_name` (`tenant_id`, `name`, `deleted`),
  KEY `idx_crm_customer_pool` (`tenant_id`, `owner_user_id`, `lock_status`, `deal_status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户';

CREATE TABLE IF NOT EXISTS `crm_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '联系人编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '联系人姓名',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(512) DEFAULT NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `mobile` varchar(32) NOT NULL DEFAULT '' COMMENT '手机号',
  `telephone` varchar(32) NOT NULL DEFAULT '' COMMENT '电话',
  `email` varchar(255) NOT NULL DEFAULT '' COMMENT '邮箱',
  `qq` bigint DEFAULT NULL COMMENT 'QQ',
  `wechat` varchar(255) NOT NULL DEFAULT '' COMMENT '微信',
  `area_id` int DEFAULT NULL COMMENT '地区编号',
  `detail_address` varchar(255) NOT NULL DEFAULT '' COMMENT '详细地址',
  `sex` int DEFAULT NULL COMMENT '性别',
  `master` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否为首要联系人',
  `post` varchar(128) NOT NULL DEFAULT '' COMMENT '职务',
  `parent_id` bigint DEFAULT NULL COMMENT '直属上级联系人编号',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_contact_tenant_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_contact_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 联系人';

CREATE TABLE IF NOT EXISTS `crm_business_status_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商机状态组编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '状态组名称',
  `dept_ids` varchar(2048) NOT NULL DEFAULT '' COMMENT '应用部门编号集合',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_status_type_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机状态组';

CREATE TABLE IF NOT EXISTS `crm_business_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商机状态编号',
  `type_id` bigint NOT NULL COMMENT '商机状态组编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '状态名称',
  `percent` int NOT NULL DEFAULT 0 COMMENT '赢单概率百分比',
  `sort` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_status_tenant_type` (`tenant_id`, `type_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机状态';

CREATE TABLE IF NOT EXISTS `crm_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商机编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '商机名称',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `follow_up_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已跟进',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `contact_next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `status_type_id` bigint NOT NULL COMMENT '商机状态组编号',
  `status_id` bigint NOT NULL COMMENT '商机状态编号',
  `end_status` int DEFAULT NULL COMMENT '结束状态',
  `end_remark` varchar(500) NOT NULL DEFAULT '' COMMENT '结束备注',
  `deal_time` datetime DEFAULT NULL COMMENT '预计成交时间',
  `total_product_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '产品总金额',
  `discount_percent` decimal(10,2) NOT NULL DEFAULT 100 COMMENT '整单折扣百分比',
  `total_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '商机总金额',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_tenant_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_business_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`),
  KEY `idx_crm_business_tenant_status` (`tenant_id`, `status_type_id`, `status_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机';

CREATE TABLE IF NOT EXISTS `crm_business_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商机产品编号',
  `business_id` bigint NOT NULL COMMENT '商机编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '产品标准单价',
  `business_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '商机销售单价',
  `count` decimal(24,4) NOT NULL DEFAULT 0 COMMENT '数量',
  `total_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '合计金额',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_business_product_tenant_business` (`tenant_id`, `business_id`, `deleted`),
  KEY `idx_crm_business_product_tenant_product` (`tenant_id`, `product_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 商机产品';

CREATE TABLE IF NOT EXISTS `crm_contact_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '联系人商机关联编号',
  `contact_id` bigint NOT NULL COMMENT '联系人编号',
  `business_id` bigint NOT NULL COMMENT '商机编号',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_contact_business_contact` (`tenant_id`, `contact_id`, `deleted`),
  KEY `idx_crm_contact_business_business` (`tenant_id`, `business_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 联系人与商机关联';

CREATE TABLE IF NOT EXISTS `crm_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品分类编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '分类名称',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类编号',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_product_category_parent` (`tenant_id`, `parent_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 产品分类';

CREATE TABLE IF NOT EXISTS `crm_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'CRM 产品编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '产品名称',
  `no` varchar(64) NOT NULL DEFAULT '' COMMENT '产品编码',
  `unit` int DEFAULT NULL COMMENT '单位',
  `price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '价格',
  `status` int NOT NULL DEFAULT 0 COMMENT '状态',
  `category_id` bigint DEFAULT NULL COMMENT '产品分类编号',
  `description` text COMMENT '产品描述',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_product_tenant_no` (`tenant_id`, `no`, `deleted`),
  KEY `idx_crm_product_tenant_category` (`tenant_id`, `category_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 产品';

CREATE TABLE IF NOT EXISTS `crm_contract_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同配置编号',
  `notify_enabled` bit(1) DEFAULT NULL COMMENT '是否开启提前提醒',
  `notify_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_contract_config_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同配置';

CREATE TABLE IF NOT EXISTS `crm_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同编号',
  `name` varchar(128) NOT NULL DEFAULT '' COMMENT '合同名称',
  `no` varchar(64) NOT NULL DEFAULT '' COMMENT '合同编码',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `business_id` bigint DEFAULT NULL COMMENT '商机编号',
  `contact_last_time` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例编号',
  `audit_status` int NOT NULL DEFAULT 10 COMMENT '审批状态',
  `order_date` datetime NOT NULL COMMENT '下单日期',
  `start_time` datetime NOT NULL COMMENT '合同开始时间',
  `end_time` datetime NOT NULL COMMENT '合同结束时间',
  `total_product_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '产品总金额',
  `discount_percent` decimal(10,2) NOT NULL DEFAULT 100 COMMENT '整单折扣百分比',
  `total_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '合同总金额',
  `sign_contact_id` bigint NOT NULL COMMENT '签约联系人编号',
  `sign_user_id` bigint NOT NULL COMMENT '公司签约人编号',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_contract_tenant_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_contract_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`),
  KEY `idx_crm_contract_tenant_no` (`tenant_id`, `no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同';

CREATE TABLE IF NOT EXISTS `crm_contract_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同产品编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `product_id` bigint NOT NULL COMMENT '产品编号',
  `product_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '产品标准单价',
  `contract_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '合同销售单价',
  `count` decimal(24,4) NOT NULL DEFAULT 0 COMMENT '数量',
  `total_price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '合计金额',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_contract_product_contract` (`tenant_id`, `contract_id`, `deleted`),
  KEY `idx_crm_contract_product_product` (`tenant_id`, `product_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 合同产品';

CREATE TABLE IF NOT EXISTS `crm_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回款编号',
  `no` varchar(64) NOT NULL DEFAULT '' COMMENT '回款编号编码',
  `plan_id` bigint DEFAULT NULL COMMENT '回款计划编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `return_time` datetime NOT NULL COMMENT '回款日期',
  `return_type` int DEFAULT NULL COMMENT '回款方式',
  `price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '回款金额',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例编号',
  `audit_status` int NOT NULL DEFAULT 10 COMMENT '审批状态',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_receivable_tenant_contract` (`tenant_id`, `contract_id`, `deleted`),
  KEY `idx_crm_receivable_tenant_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_receivable_tenant_owner` (`tenant_id`, `owner_user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 回款';

CREATE TABLE IF NOT EXISTS `crm_receivable_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回款计划编号',
  `period` int NOT NULL COMMENT '期数',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `contract_id` bigint NOT NULL COMMENT '合同编号',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `return_time` datetime NOT NULL COMMENT '计划回款日期',
  `return_type` int DEFAULT NULL COMMENT '计划回款方式',
  `price` decimal(24,2) NOT NULL DEFAULT 0 COMMENT '计划回款金额',
  `receivable_id` bigint DEFAULT NULL COMMENT '实际回款编号',
  `remind_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `remind_time` datetime DEFAULT NULL COMMENT '提醒时间',
  `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_receivable_plan_contract` (`tenant_id`, `contract_id`, `deleted`),
  KEY `idx_crm_receivable_plan_customer` (`tenant_id`, `customer_id`, `deleted`),
  KEY `idx_crm_receivable_plan_return_time` (`tenant_id`, `return_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 回款计划';

CREATE TABLE IF NOT EXISTS `crm_customer_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户公海配置编号',
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用客户公海',
  `contact_expire_days` int DEFAULT NULL COMMENT '未跟进放入公海天数',
  `deal_expire_days` int DEFAULT NULL COMMENT '未成交放入公海天数',
  `notify_enabled` bit(1) DEFAULT NULL COMMENT '是否开启提前提醒',
  `notify_days` int DEFAULT NULL COMMENT '提前提醒天数',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_customer_pool_config_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户公海配置';

CREATE TABLE IF NOT EXISTS `crm_customer_limit_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户限制配置编号',
  `type` int NOT NULL COMMENT '规则类型',
  `user_ids` varchar(2048) NOT NULL DEFAULT '' COMMENT '适用用户编号集合',
  `dept_ids` varchar(2048) NOT NULL DEFAULT '' COMMENT '适用部门编号集合',
  `max_count` int NOT NULL COMMENT '数量上限',
  `deal_count_enabled` bit(1) DEFAULT NULL COMMENT '成交客户是否计入拥有数',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_customer_limit_config_tenant_type` (`tenant_id`, `type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 客户限制配置';

CREATE TABLE IF NOT EXISTS `crm_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '跟进记录编号',
  `biz_type` int NOT NULL COMMENT '业务类型',
  `biz_id` bigint NOT NULL COMMENT '业务编号',
  `type` int NOT NULL COMMENT '跟进类型',
  `content` text NOT NULL COMMENT '跟进内容',
  `next_time` datetime DEFAULT NULL COMMENT '下次联系时间',
  `pic_urls` varchar(4096) NOT NULL DEFAULT '' COMMENT '图片地址集合',
  `file_urls` varchar(4096) NOT NULL DEFAULT '' COMMENT '附件地址集合',
  `business_ids` varchar(2048) NOT NULL DEFAULT '' COMMENT '关联商机编号集合',
  `contact_ids` varchar(2048) NOT NULL DEFAULT '' COMMENT '关联联系人编号集合',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_follow_up_record_biz` (`tenant_id`, `biz_type`, `biz_id`, `deleted`),
  KEY `idx_crm_follow_up_record_create_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 跟进记录';

CREATE TABLE IF NOT EXISTS `crm_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据权限编号',
  `biz_type` int NOT NULL COMMENT '业务类型',
  `biz_id` bigint NOT NULL COMMENT '业务编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `level` int NOT NULL COMMENT '权限级别',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_crm_permission_biz` (`tenant_id`, `biz_type`, `biz_id`, `deleted`),
  KEY `idx_crm_permission_user` (`tenant_id`, `user_id`, `biz_type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM 数据权限';

-- Validate that every CRM table required by the current module is present.
DROP TEMPORARY TABLE IF EXISTS `oakved_crm_enable_guard`;
CREATE TEMPORARY TABLE `oakved_crm_enable_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_oakved_crm_enable_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE (
  SELECT COUNT(*)
  FROM `information_schema`.`tables`
  WHERE `table_schema` = DATABASE()
    AND `table_name` IN (
      'crm_business', 'crm_business_product', 'crm_business_status',
      'crm_business_status_type', 'crm_clue', 'crm_contact',
      'crm_contact_business', 'crm_contract', 'crm_contract_config',
      'crm_contract_product', 'crm_customer', 'crm_customer_limit_config',
      'crm_customer_pool_config', 'crm_follow_up_record', 'crm_permission',
      'crm_product', 'crm_product_category', 'crm_receivable',
      'crm_receivable_plan'
    )
) <> 19;

-- Production uses tenant 162 for VANZ. A fresh local baseline does not seed
-- tenant 162, so tenant 121 is the deterministic preview fallback.
SET @oakved_crm_target_tenant_id = COALESCE(
  (
    SELECT `id`
    FROM `system_tenant`
    WHERE `id` = 162 AND `status` = 0 AND `deleted` = b'0'
    LIMIT 1
  ),
  (
    SELECT `id`
    FROM `system_tenant`
    WHERE `id` = 121 AND `status` = 0 AND `deleted` = b'0'
    LIMIT 1
  )
);

SET @oakved_crm_source_package_id = (
  SELECT `package_id`
  FROM `system_tenant`
  WHERE `id` = @oakved_crm_target_tenant_id AND `deleted` = b'0'
  LIMIT 1
);

SET @oakved_crm_package_marker = CONCAT(
  'oakved-crm-full-v030:tenant-', COALESCE(@oakved_crm_target_tenant_id, 'missing')
);

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE @oakved_crm_target_tenant_id IS NULL
   OR @oakved_crm_source_package_id IS NULL
   OR (
     SELECT COUNT(*)
     FROM `system_menu`
     WHERE `id` = 2397
       AND `parent_id` = 0
       AND `path` = '/crm'
       AND `type` = 1
       AND `status` = 0
       AND `deleted` = b'0'
   ) <> 1;

INSERT INTO `system_tenant_package`
(`name`, `status`, `remark`, `menu_ids`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`)
SELECT 'Oakved CRM 全功能', source.`status`, @oakved_crm_package_marker,
       source.`menu_ids`, 'V031', CURRENT_TIMESTAMP,
       'V031', CURRENT_TIMESTAMP, b'0'
FROM `system_tenant_package` AS source
WHERE source.`id` = @oakved_crm_source_package_id
  AND source.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_tenant_package`
    WHERE BINARY `remark` = BINARY @oakved_crm_package_marker
      AND `deleted` = b'0'
  );

SET @oakved_crm_package_id = (
  SELECT `id`
  FROM `system_tenant_package`
  WHERE BINARY `remark` = BINARY @oakved_crm_package_marker
    AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE @oakved_crm_package_id IS NULL
   OR (
     SELECT COUNT(*)
     FROM `system_tenant_package`
     WHERE `id` = @oakved_crm_package_id
       AND `status` = 0
       AND JSON_VALID(`menu_ids`)
       AND `deleted` = b'0'
   ) <> 1;

DROP TEMPORARY TABLE IF EXISTS `oakved_crm_menu_scope`;
CREATE TEMPORARY TABLE `oakved_crm_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `oakved_crm_menu_scope` (`menu_id`)
WITH RECURSIVE `crm_menu_tree` AS (
  SELECT `id`
  FROM `system_menu`
  WHERE `id` = 2397 AND `deleted` = b'0'
  UNION ALL
  SELECT child.`id`
  FROM `system_menu` AS child
  INNER JOIN `crm_menu_tree` AS parent ON child.`parent_id` = parent.`id`
  WHERE child.`deleted` = b'0'
)
SELECT `id` FROM `crm_menu_tree`;

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE (SELECT COUNT(*) FROM `oakved_crm_menu_scope`) < 1;

DROP TEMPORARY TABLE IF EXISTS `oakved_crm_package_menu`;
CREATE TEMPORARY TABLE `oakved_crm_package_menu` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT IGNORE INTO `oakved_crm_package_menu` (`menu_id`)
SELECT package_menu.`menu_id`
FROM `system_tenant_package` AS package
INNER JOIN JSON_TABLE(
  package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
) AS package_menu
WHERE package.`id` = @oakved_crm_package_id;

INSERT IGNORE INTO `oakved_crm_package_menu` (`menu_id`)
SELECT `menu_id` FROM `oakved_crm_menu_scope`;

UPDATE `system_tenant_package`
SET `menu_ids` = (
      SELECT JSON_ARRAYAGG(ordered_menu.`menu_id`)
      FROM (
        SELECT `menu_id`
        FROM `oakved_crm_package_menu`
        ORDER BY `menu_id`
      ) AS ordered_menu
    ),
    `updater` = 'V031',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_crm_package_id;

UPDATE `system_tenant`
SET `package_id` = @oakved_crm_package_id,
    `updater` = 'V031',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @oakved_crm_target_tenant_id
  AND `deleted` = b'0';

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`,
 `deleted`, `tenant_id`)
SELECT role.`id`, scope.`menu_id`, 'V031', CURRENT_TIMESTAMP,
       'V031', CURRENT_TIMESTAMP, b'0', @oakved_crm_target_tenant_id
FROM `system_role` AS role
CROSS JOIN `oakved_crm_menu_scope` AS scope
WHERE role.`tenant_id` = @oakved_crm_target_tenant_id
  AND role.`code` = 'tenant_admin'
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` AS role_menu
    WHERE role_menu.`role_id` = role.`id`
      AND role_menu.`menu_id` = scope.`menu_id`
      AND role_menu.`tenant_id` = @oakved_crm_target_tenant_id
      AND role_menu.`deleted` = b'0'
  );

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `oakved_crm_menu_scope` AS scope
  WHERE NOT EXISTS (
    SELECT 1
    FROM `system_tenant_package` AS package
    INNER JOIN JSON_TABLE(
      package.`menu_ids`, '$[*]' COLUMNS (`menu_id` bigint PATH '$')
    ) AS package_menu ON package_menu.`menu_id` = scope.`menu_id`
    WHERE package.`id` = @oakved_crm_package_id
  )
);

INSERT INTO `oakved_crm_enable_guard` (`valid`)
SELECT 0
WHERE EXISTS (
  SELECT 1
  FROM `system_role` AS role
  CROSS JOIN `oakved_crm_menu_scope` AS scope
  WHERE role.`tenant_id` = @oakved_crm_target_tenant_id
    AND role.`code` = 'tenant_admin'
    AND role.`type` = 1
    AND role.`status` = 0
    AND role.`deleted` = b'0'
    AND NOT EXISTS (
      SELECT 1
      FROM `system_role_menu` AS role_menu
      WHERE role_menu.`role_id` = role.`id`
        AND role_menu.`menu_id` = scope.`menu_id`
        AND role_menu.`tenant_id` = @oakved_crm_target_tenant_id
        AND role_menu.`deleted` = b'0'
    )
);

DROP TEMPORARY TABLE `oakved_crm_package_menu`;
DROP TEMPORARY TABLE `oakved_crm_menu_scope`;
DROP TEMPORARY TABLE `oakved_crm_enable_guard`;
