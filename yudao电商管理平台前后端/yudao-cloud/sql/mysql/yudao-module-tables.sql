SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE IF NOT EXISTS `member_user`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `nickname`    varchar(30)  NOT NULL DEFAULT '',
    `name`        varchar(30)  NULL,
    sex           tinyint      null comment '鎬у埆',
    birthday      datetime     null comment '鍑虹敓鏃ユ湡',
    area_id       int          null comment '鎵€鍦ㄥ湴',
    mark          varchar(255) null comment '鐢ㄦ埛澶囨敞',
    point         int                   default 0 null comment '绉垎',
    `avatar`      varchar(255) NOT NULL DEFAULT '',
    `status`      tinyint      NOT NULL,
    `mobile`      varchar(11)  NULL,
    `email`       varchar(255) NULL,
    `email_verified` bit(1) NOT NULL DEFAULT b'0',
    `email_verified_time` datetime NULL,
    `trade_id`    varchar(64)  NULL,
    `password`    varchar(100) NOT NULL DEFAULT '',
    `register_ip` varchar(32)  NOT NULL,
    `register_terminal` tinyint NULL,
    `login_ip`    varchar(50)  NULL     DEFAULT '',
    `login_date`  datetime     NULL     DEFAULT NULL,
    `tag_ids`     varchar(255) NULL     DEFAULT NULL,
    `level_id`    bigint       NULL     DEFAULT NULL,
    `experience`  bigint       NULL     DEFAULT NULL,
    `group_id`    bigint       NULL     DEFAULT NULL,
    `creator`     varchar(64)  NULL     DEFAULT '',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(64)  NULL     DEFAULT '',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)       NOT NULL DEFAULT 0,
    `tenant_id`   bigint       not null DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`)
);

CREATE TABLE IF NOT EXISTS `member_address` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) NOT NULL,
    `name` varchar(10) NOT NULL,
    `mobile` varchar(20) NOT NULL,
    `area_id` bigint(20) NOT NULL,
    `detail_address` varchar(250) NOT NULL,
    `default_status` bit(1) NOT NULL,
    `address_verification` json DEFAULT NULL COMMENT 'Address verification audit payload',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `creator` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `updater` varchar(64) DEFAULT '',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `member_tag`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255)  NOT NULL,
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`   bigint   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `member_level`
(
    `id`             bigint   NOT NULL AUTO_INCREMENT,
    `name`           varchar(255)  NOT NULL,
    `experience`     int      NOT NULL,
    `level`          int      NOT NULL,
    `discount_percent`       int      NOT NULL,
    `icon`           varchar(255)  NOT NULL,
    `background_url` varchar(255)  NOT NULL,
    `creator`        varchar(255)           DEFAULT '',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        varchar(255)           DEFAULT '',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`      bigint   not null DEFAULT 0,
    `status` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `member_group`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255)  NOT NULL,
    `remark`      varchar(255)  NOT NULL,
    `status` tinyint NOT NULL DEFAULT 0,
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`      bigint   not null DEFAULT 0,
    PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `member_brokerage_record`
(
    `id`            int      NOT NULL AUTO_INCREMENT,
    `user_id`       bigint   NOT NULL,
    `biz_id`        varchar(255)  NOT NULL,
    `biz_type`      varchar(255)  NOT NULL,
    `title`         varchar(255)  NOT NULL,
    `price`         int      NOT NULL,
    `total_price`   int      NOT NULL,
    `description`   varchar(255)  NOT NULL,
    `status`        varchar(255)  NOT NULL,
    `frozen_days`   int      NOT NULL,
    `unfreeze_time` varchar(255),
    `creator`       varchar(255)           DEFAULT '',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`       varchar(255)           DEFAULT '',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`      bigint   not null DEFAULT 0,
    PRIMARY KEY (`id`)
);


CREATE TABLE IF NOT EXISTS `pay_app` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_key`              varchar(64)   NOT NULL,
    `name`              varchar(64)   NOT NULL,
    `status`            tinyint       NOT NULL,
    `remark`            varchar(255)           DEFAULT NULL,
    `order_notify_url`    varchar(1024) NOT NULL,
    `refund_notify_url` varchar(1024) NOT NULL,
    `transfer_notify_url` varchar(1024) DEFAULT NULL,
    `creator`           varchar(64)            DEFAULT '',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           varchar(64)            DEFAULT '',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

SET @pay_app_transfer_notify_url_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                                           WHERE TABLE_SCHEMA = DATABASE()
                                             AND TABLE_NAME = 'pay_app'
                                             AND COLUMN_NAME = 'transfer_notify_url') = 0,
                                          'ALTER TABLE `pay_app` ADD COLUMN `transfer_notify_url` varchar(1024) DEFAULT NULL AFTER `refund_notify_url`',
                                          'SELECT ''pay_app.transfer_notify_url already exists'' AS message');
PREPARE pay_app_stmt FROM @pay_app_transfer_notify_url_sql; EXECUTE pay_app_stmt; DEALLOCATE PREPARE pay_app_stmt;

CREATE TABLE IF NOT EXISTS `pay_channel` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code`        varchar(32)    NOT NULL,
    `status`      tinyint(4)     NOT NULL,
    `remark`      varchar(255)            DEFAULT NULL,
    `fee_rate`    double         NOT NULL DEFAULT 0,
    `app_id`      bigint(20)     NOT NULL,
    `config`      varchar(10240) NOT NULL,
    `creator`     varchar(64)    NULL     DEFAULT '',
    `create_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(64)    NULL     DEFAULT '',
    `update_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)         NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_id`               bigint(20)    NOT NULL,
    `channel_id`           bigint(20)             DEFAULT NULL,
    `channel_code`         varchar(32)            DEFAULT NULL,
    `merchant_order_id`    varchar(64)   NOT NULL,
    `subject`              varchar(32)   NOT NULL,
    `body`                 varchar(128)  NOT NULL,
    `notify_url`           varchar(1024) NOT NULL,
    `price`                bigint(20)    NOT NULL,
    `channel_fee_rate`     double                 DEFAULT 0,
    `channel_fee_price`    bigint(20)             DEFAULT 0,
    `status`               tinyint(4)    NOT NULL,
    `user_ip`              varchar(50)   NOT NULL,
    `user_id`              bigint(20)             DEFAULT NULL,
    `user_type`            tinyint(4)             DEFAULT NULL,
    `expire_time`          timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `success_time`         datetime(0)            DEFAULT CURRENT_TIMESTAMP,
    `notify_time`          datetime(0)            DEFAULT CURRENT_TIMESTAMP,
    `extension_id` bigint(20)             DEFAULT NULL,
    `no`                   varchar(64)   NULL,
    `refund_price`         bigint(20)    NOT NULL,
    `channel_user_id`      varchar(255)           DEFAULT NULL,
    `channel_order_no`     varchar(64)            DEFAULT NULL,
    `creator`              varchar(64)            DEFAULT '',
    `create_time`          datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`              varchar(64)            DEFAULT '',
    `update_time`          datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_order_channel_order_no` (`channel_id`, `channel_order_no`)
);

CREATE TABLE IF NOT EXISTS `pay_order_extension` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no`           varchar(64)         NOT NULL,
    `order_id`           bigint(20)    NOT NULL,
    `channel_id`         bigint(20)    NOT NULL,
    `channel_code`       varchar(32)   NOT NULL,
    `user_ip`            varchar(50)   NULL     DEFAULT NULL,
    `status`             tinyint(4)    NOT NULL,
    `channel_extras`     varchar(1024) NULL     DEFAULT NULL,
    `channel_error_code`  varchar(64)  NULL,
    `channel_error_msg` varchar(64)    NULL,
    `channel_notify_data` varchar(1024)  NULL,
    `creator`            varchar(64)   NULL     DEFAULT '',
    `create_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(64)   NULL     DEFAULT '',
    `update_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_refund` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no`           varchar(64)         NOT NULL,
    `app_id`             bigint(20)    NOT NULL,
    `channel_id`         bigint(20)    NOT NULL,
    `channel_code`       varchar(32)   NOT NULL,
    `order_id`           bigint(20)    NOT NULL,
    `order_no`           varchar(64)    NOT NULL,
    `merchant_order_id`  varchar(64)   NOT NULL,
    `merchant_refund_id` varchar(64)   NOT NULL,
    `notify_url`         varchar(1024) NOT NULL,
    `status`             tinyint(4)    NOT NULL,
    `pay_price`         bigint(20)    NOT NULL,
    `refund_price`      bigint(20)    NOT NULL,
    `reason`             varchar(256)  NOT NULL,
    `user_ip`            varchar(50)   NULL     DEFAULT NULL,
    `user_id`            bigint(20)    NULL     DEFAULT NULL,
    `user_type`          tinyint(4)    NULL     DEFAULT NULL,
    `channel_order_no`   varchar(64)   NOT NULL,
    `channel_refund_no`  varchar(64)   NULL     DEFAULT NULL,
    `success_time`       datetime(0)   NULL     DEFAULT NULL,
    `channel_error_code` varchar(128)  NULL     DEFAULT NULL,
    `channel_error_msg`  varchar(256)  NULL     DEFAULT NULL,
    `channel_notify_data` varchar(1024)  NULL,
    `creator`            varchar(64)   NULL     DEFAULT '',
    `create_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(64)   NULL     DEFAULT '',
    `update_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_notify_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_id`             bigint(20)    NOT NULL,
    `type`               tinyint(4)    NOT NULL,
    `data_id`           bigint(20)    NOT NULL,
    `merchant_order_id`           varchar(64)    NOT NULL,
    `status`             tinyint(4)    NOT NULL,
    `next_notify_time`       datetime(0)   NULL     DEFAULT NULL,
    `last_execute_time`       datetime(0)   NULL     DEFAULT NULL,
    `notify_times`         int    NOT NULL,
    `max_notify_times`         int    NOT NULL,
    `notify_url`         varchar(1024) NOT NULL,
    `creator`            varchar(64)   NULL     DEFAULT '',
    `create_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(64)   NULL     DEFAULT '',
    `update_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id`           bigint(20)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_notify_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `task_id`             bigint(20)    NOT NULL,
    `notify_times`         int    NOT NULL,
    `response`         varchar(1024) NOT NULL,
    `status`             tinyint(4)    NOT NULL,
    `creator`            varchar(64)   NULL     DEFAULT '',
    `create_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(64)   NULL     DEFAULT '',
    `update_time`        datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_transfer` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no`                   varchar(64)   NOT NULL,
    `app_id`               bigint(20)    NOT NULL,
    `channel_id`           bigint(20)    NOT NULL,
    `channel_code`         varchar(32)   NOT NULL,
    `user_id`              bigint(20)    NULL     DEFAULT NULL,
    `user_type`            tinyint(4)    NULL     DEFAULT NULL,
    `merchant_transfer_id` varchar(64)   NOT NULL,
    `price`                bigint(20)    NOT NULL,
    `subject`              varchar(256)  NOT NULL,
    `user_account`         varchar(256)  NOT NULL,
    `user_name`            varchar(64)   NULL     DEFAULT NULL,
    `status`               tinyint(4)    NOT NULL,
    `notify_url`           varchar(1024) NULL     DEFAULT NULL,
    `channel_transfer_no`  varchar(64)   NULL     DEFAULT NULL,
    `success_time`         datetime(0)   NULL     DEFAULT NULL,
    `channel_error_code`   varchar(128)  NULL     DEFAULT NULL,
    `channel_error_msg`    varchar(256)  NULL     DEFAULT NULL,
    `channel_notify_data`  varchar(1024) NULL     DEFAULT NULL,
    `channel_extras`       varchar(1024) NULL     DEFAULT NULL,
    `creator`              varchar(64)   NULL     DEFAULT '',
    `create_time`          datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`              varchar(64)   NULL     DEFAULT '',
    `update_time`          datetime(0)   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              bit(1)        NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pay_wallet` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `user_type` tinyint(4) NOT NULL,
    `balance` int NOT NULL DEFAULT 0,
    `freeze_price` int NOT NULL DEFAULT 0,
    `total_expense` int NOT NULL DEFAULT 0,
    `total_recharge` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_wallet_user_type` (`user_id`, `user_type`)
);

CREATE TABLE IF NOT EXISTS `pay_wallet_recharge_package` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(64) NOT NULL,
    `pay_price` int NOT NULL,
    `bonus_price` int NOT NULL DEFAULT 0,
    `status` tinyint(4) NOT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_wallet_recharge_package_name` (`name`)
);

CREATE TABLE IF NOT EXISTS `pay_wallet_recharge` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `wallet_id` bigint NOT NULL,
    `total_price` int NOT NULL,
    `pay_price` int NOT NULL,
    `bonus_price` int NOT NULL DEFAULT 0,
    `package_id` bigint DEFAULT NULL,
    `pay_status` bit(1) NOT NULL DEFAULT b'0',
    `pay_order_id` bigint DEFAULT NULL,
    `pay_channel_code` varchar(32) DEFAULT NULL,
    `pay_time` datetime(0) DEFAULT NULL,
    `pay_refund_id` bigint DEFAULT NULL,
    `refund_total_price` int DEFAULT NULL,
    `refund_pay_price` int DEFAULT NULL,
    `refund_bonus_price` int DEFAULT NULL,
    `refund_time` datetime(0) DEFAULT NULL,
    `refund_status` tinyint(4) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_pay_wallet_recharge_wallet_id_pay_status` (`wallet_id`, `pay_status`),
    KEY `idx_pay_wallet_recharge_pay_order_id` (`pay_order_id`),
    KEY `idx_pay_wallet_recharge_pay_refund_id` (`pay_refund_id`)
);

CREATE TABLE IF NOT EXISTS `pay_wallet_transaction` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(64) NOT NULL,
    `wallet_id` bigint NOT NULL,
    `biz_type` tinyint(4) NOT NULL,
    `biz_id` varchar(64) NOT NULL,
    `title` varchar(255) NOT NULL,
    `price` int NOT NULL,
    `balance` int NOT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_wallet_transaction_no` (`no`),
    UNIQUE KEY `uk_pay_wallet_transaction_biz` (`biz_id`, `biz_type`),
    KEY `idx_pay_wallet_transaction_wallet_id_create_time` (`wallet_id`, `create_time`)
);


CREATE TABLE IF NOT EXISTS `product_sku` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `spu_id` bigint NOT NULL,
    `properties` varchar(512)  DEFAULT NULL,
    `price` int NOT NULL DEFAULT -1,
    `market_price` int DEFAULT NULL,
    `cost_price` int NOT NULL DEFAULT -1,
    `bar_code` varchar(64)  DEFAULT NULL,
    `pic_url` varchar(256)  NOT NULL,
    `stock` int DEFAULT NULL,
    `weight` double DEFAULT NULL,
    `volume` double DEFAULT NULL,
    `first_brokerage_price` int DEFAULT NULL,
    `second_brokerage_price` int DEFAULT NULL,
    `sub_commission_first_price` int DEFAULT NULL,
    `sub_commission_second_price` int DEFAULT NULL,
    `sales_count` int DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_spu` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(128) NOT NULL,
    `keyword` varchar(256) NOT NULL,
    `introduction` varchar(256) NOT NULL,
    `description` text NOT NULL,
    `bar_code` varchar(64) NOT NULL DEFAULT '',
    `category_id` bigint NOT NULL,
    `brand_id` int DEFAULT NULL,
    `pic_url` varchar(256) NOT NULL,
    `slider_pic_urls` varchar(2000)  DEFAULT '',
    `detail_config` json DEFAULT NULL COMMENT 'Furniture Web product detail page configuration',
    `video_url` varchar(256) DEFAULT NULL,
    `unit` tinyint NOT NULL DEFAULT 1,
    `sort` int NOT NULL DEFAULT 0,
    `status` tinyint NOT NULL,
    `spec_type` bit(1) NOT NULL,
    `price` int NOT NULL DEFAULT -1,
    `market_price` int NOT NULL,
    `cost_price` int NOT NULL DEFAULT -1,
    `stock` int NOT NULL DEFAULT 0,
    `delivery_types` varchar(255) DEFAULT NULL,
    `delivery_template_id` bigint NOT NULL DEFAULT 0,
    `recommend_hot` bit(1) NOT NULL DEFAULT b'0',
    `recommend_benefit` bit(1) NOT NULL DEFAULT b'0',
    `recommend_best` bit(1) NOT NULL DEFAULT b'0',
    `recommend_new` bit(1) NOT NULL DEFAULT b'0',
    `recommend_good` bit(1) NOT NULL DEFAULT b'0',
    `give_integral` int NOT NULL,
    `give_coupon_template_ids` varchar(512)  DEFAULT '',
    `sub_commission_type` bit(1) NOT NULL,
    `activity_orders` varchar(16) NOT NULL DEFAULT '',
    `sales_count` int DEFAULT 0,
    `virtual_sales_count` int DEFAULT 0,
    `browse_count` int DEFAULT 0,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_category` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `parent_id` bigint NOT NULL,
    `name` varchar(255) NOT NULL,
    `pic_url` varchar(255) NOT NULL,
    `big_pic_url` varchar(255) DEFAULT NULL,
    `sort` int DEFAULT 0,
    `status` tinyint NOT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_brand` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL,
    `pic_url` varchar(255) NOT NULL,
    `sort` int DEFAULT 0,
    `description` varchar(1024) DEFAULT NULL,
    `status` tinyint NOT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_property` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(64) DEFAULT NULL,
    `status` tinyint DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    `remark` varchar(255) DEFAULT NULL,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_property_value` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `property_id` bigint DEFAULT NULL,
    `name` varchar(128) DEFAULT NULL,
    `status` tinyint DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    `remark` varchar(255) DEFAULT NULL,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `product_comment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `user_nickname` varchar(255) DEFAULT NULL,
    `user_avatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `anonymous` bit(1) DEFAULT NULL,
    `order_id` bigint DEFAULT NULL,
    `order_item_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `spu_name` varchar(255) DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `visible` bit(1) DEFAULT NULL,
    `scores` tinyint DEFAULT NULL,
    `description_scores` tinyint DEFAULT NULL,
    `benefit_scores` tinyint DEFAULT NULL,
    `content` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `pic_urls` varchar(4096) DEFAULT NULL,
    `reply_status` bit(1) DEFAULT NULL,
    `reply_user_id` bigint DEFAULT NULL,
    `reply_content` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `reply_time` datetime DEFAULT NULL,
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `market_activity`
(
    `id`                    bigint(20)  NOT NULL AUTO_INCREMENT,
    `title`                 varchar(50) NOT NULL,
    `activity_type`         tinyint(4)  NOT NULL,
    `status`                tinyint(4)  NOT NULL,
    `start_time`            datetime    NOT NULL,
    `end_time`              datetime    NOT NULL,
    `invalid_time`          datetime,
    `delete_time`           datetime,
    `time_limited_discount` varchar(2000),
    `full_privilege`        varchar(2000),
    `creator`               varchar(64)          DEFAULT '',
    `create_time`           datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`               varchar(64)          DEFAULT '',
    `update_time`           datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`               bit(1)         NOT NULL DEFAULT b'0',
    `tenant_id`             bigint(20)  NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_coupon_template`
(
    `id`                   bigint   NOT NULL AUTO_INCREMENT,
    `name`                 varchar(255)  NOT NULL,
    `description`          varchar(255),
    `status`               int      NOT NULL,
    `total_count`          int      NOT NULL,
    `take_limit_count`     int      NOT NULL,
    `take_type`            int      NOT NULL,
    `use_price`            int      NOT NULL,
    `product_scope`        int      NOT NULL,
    `product_scope_values` varchar(255),
    `validity_type`        int      NOT NULL,
    `valid_start_time`     datetime,
    `valid_end_time`       datetime,
    `fixed_start_term`     int,
    `fixed_end_term`       int,
    `discount_type`        int      NOT NULL,
    `discount_percent`     int,
    `discount_price`       int,
    `discount_limit_price` int,
    `take_count`           int      NOT NULL DEFAULT 0,
    `use_count`            int      NOT NULL DEFAULT 0,
    `creator`              varchar(255)           DEFAULT '',
    `create_time`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`              varchar(255)           DEFAULT '',
    `update_time`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_coupon`
(
    `id`                   bigint   NOT NULL AUTO_INCREMENT,
    `template_id`          bigint   NOT NULL,
    `name`                 varchar(255)  NOT NULL,
    `status`               int      NOT NULL,
    `user_id`              bigint   NOT NULL,
    `take_type`            int      NOT NULL,
    `use_price`            int      NOT NULL,
    `valid_start_time`     datetime NOT NULL,
    `valid_end_time`       datetime NOT NULL,
    `product_scope`        int      NOT NULL,
    `product_scope_values` varchar(255),
    `discount_type`        int      NOT NULL,
    `discount_percent`     int,
    `discount_price`       int,
    `discount_limit_price` int,
    `use_order_id`         bigint,
    `use_time`             datetime,
    `creator`              varchar(255)           DEFAULT '',
    `create_time`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`              varchar(255)           DEFAULT '',
    `update_time`          datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_reward_activity`
(
    `id`              bigint   NOT NULL AUTO_INCREMENT,
    `name`            varchar(255)  NOT NULL,
    `status`          int      NOT NULL,
    `start_time`      datetime NOT NULL,
    `end_time`        datetime NOT NULL,
    `remark`          varchar(255),
    `condition_type`  int      NOT NULL,
    `product_scope`   int      NOT NULL,
    `product_spu_ids` varchar(255),
    `rules`           varchar(255),
    `creator`         varchar(255)           DEFAULT '',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`         varchar(255)           DEFAULT '',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_discount_activity`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255)  NOT NULL,
    `status`      int      NOT NULL,
    `start_time`  datetime NOT NULL,
    `end_time`    datetime NOT NULL,
    `remark`      varchar(255),
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_discount_product`
(
    `id`                  bigint   NOT NULL AUTO_INCREMENT,
    `activity_id`         bigint   NOT NULL,
    `spu_id`              bigint   NOT NULL,
    `sku_id`              bigint   NOT NULL,
    `discount_type`       int      NOT NULL,
    `discount_percent`    int,
    `discount_price`      int,
    `activity_name`       varchar(255)  NOT NULL,
    `activity_status`     int      NOT NULL,
    `activity_start_time` datetime NOT NULL,
    `activity_end_time`   datetime NOT NULL,
    `creator`             varchar(255)           DEFAULT '',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`             varchar(255)           DEFAULT '',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_seckill_activity`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT,
    `spu_id`             bigint   NOT NULL,
    `name`               varchar(255)  NOT NULL,
    `status`             int      NOT NULL,
    `remark`             varchar(255),
    `start_time`         varchar(255)  NOT NULL,
    `end_time`           varchar(255)  NOT NULL,
    `sort`               int      NOT NULL,
    `config_ids`         varchar(255)  NOT NULL,
    `order_count`        int      NOT NULL,
    `user_count`         int      NOT NULL,
    `total_price`        int      NOT NULL,
    `total_limit_count`  int,
    `single_limit_count` int,
    `stock`              int,
    `total_stock`        int,
    `creator`            varchar(255)           DEFAULT '',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(255)           DEFAULT '',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`          bigint   NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_seckill_config`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255)  NOT NULL,
    `start_time`  varchar(255)  NOT NULL,
    `end_time`    varchar(255)  NOT NULL,
    `pic_url`     varchar(255)  NOT NULL,
    `status`      int      NOT NULL,
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`   bigint   NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_combination_activity`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT,
    `name`               varchar(255)  NOT NULL,
    `spu_id`             bigint,
    `total_limit_count`  int      NOT NULL,
    `single_limit_count` int      NOT NULL,
    `start_time`         varchar(255)  NOT NULL,
    `end_time`           varchar(255)  NOT NULL,
    `user_size`          int      NOT NULL,
    `total_num`          int      NOT NULL,
    `success_num`        int      NOT NULL,
    `order_user_count`   int      NOT NULL,
    `virtual_group`      int      NOT NULL,
    `status`             int      NOT NULL,
    `limit_duration`     int      NOT NULL,
    `creator`            varchar(255)           DEFAULT '',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(255)           DEFAULT '',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`          bigint   NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_article_category`
(
    `id`          bigint   NOT NULL AUTO_INCREMENT,
    `name`        varchar(255)  NOT NULL,
    `pic_url`     varchar(255),
    `status`      int      NOT NULL,
    `sort`        int      NOT NULL,
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`   bigint   NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_article`
(
    `id`               bigint   NOT NULL AUTO_INCREMENT,
    `category_id`      bigint   NOT NULL,
    `title`            varchar(255)  NOT NULL,
    `author`           varchar(255),
    `pic_url`          varchar(255)  NOT NULL,
    `introduction`     varchar(255),
    `browse_count`     varchar(255),
    `sort`             int      NOT NULL,
    `status`           int      NOT NULL,
    `spu_id`           bigint   NOT NULL,
    `recommend_hot`    bit(1)      NOT NULL,
    `recommend_banner` bit(1)      NOT NULL,
    `content`          varchar(255)  NOT NULL,
    `creator`          varchar(255)           DEFAULT '',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`          varchar(255)           DEFAULT '',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`          bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`        bigint   NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `promotion_diy_template`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT,
    `name`               varchar(255)  NOT NULL,
    `used`               bit(1)      NOT NULL,
    `used_time`          varchar(255),
    `remark`             varchar(255),
    `preview_pic_urls`   varchar(255),
    `property`           varchar(255)  NOT NULL,
    `creator`            varchar(255)           DEFAULT '',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(255)           DEFAULT '',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`          bigint   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `promotion_diy_page`
(
    `id`                 bigint   NOT NULL AUTO_INCREMENT,
    `template_id`        bigint   NOT NULL,
    `name`               varchar(255)  NOT NULL,
    `remark`             varchar(255),
    `preview_pic_urls`   varchar(255),
    `property`           varchar(255),
    `creator`            varchar(255)           DEFAULT '',
    `create_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`            varchar(255)           DEFAULT '',
    `update_time`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`          bigint   NOT NULL,
    PRIMARY KEY (`id`)
);


CREATE TABLE IF NOT EXISTS `trade_order`
(
    `id`                      bigint   NOT NULL AUTO_INCREMENT,
    `no`                      varchar(255)  NOT NULL,
    `type`                    int      NOT NULL,
    `terminal`                int      NOT NULL,
    `user_id`                 bigint   NOT NULL,
    `user_ip`                 varchar(255)  NOT NULL,
    `user_remark`             varchar(255),
    `address_verification`    json                  DEFAULT NULL COMMENT 'Address verification audit payload',
    `status`                  int      NOT NULL,
    `product_count`           int      NOT NULL,
    `cancel_type`             int,
    `remark`                  varchar(255),
    `comment_status`          bit(1),
    `brokerage_user_id`       bigint,
    `pay_status`              bit(1)      NOT NULL,
    `pay_time`                datetime,
    `finish_time`             datetime,
    `cancel_time`             datetime,
    `total_price`             int      NULL,
    `order_price`             int      NULL,
    `discount_price`          int      NOT NULL,
    `delivery_price`          int      NOT NULL,
    `adjust_price`            int      NOT NULL,
    `pay_price`               int      NOT NULL,
    `delivery_type`           int      NOT NULL,
    `pay_order_id`            bigint,
    `pay_channel_code`        varchar(255),
    `delivery_template_id`    bigint,
    `logistics_id`            bigint,
    `logistics_no`            varchar(255),
    `delivery_time`           datetime,
    `receive_time`            datetime,
    `receiver_name`           varchar(255)  NOT NULL,
    `receiver_mobile`         varchar(255)  NOT NULL,
    `receiver_area_id`        int      NOT NULL,
    `receiver_post_code`      int,
    `receiver_detail_address` varchar(255)  NOT NULL,
    `pick_up_store_id`        bigint     NULL,
    `pick_up_verify_code`     varchar(255)  NULL,
    `refund_status`           int      NULL,
    `refund_price`            int      NULL,
    `after_sale_status`       int      NULL,
    `coupon_id`               bigint   NOT NULL,
    `coupon_price`            int      NOT NULL,
    `use_point`               int      NULL,
    `point_price`             int      NOT NULL,
    `give_point`              int      NULL,
    `refund_point`            int      NULL,
    `vip_price`               int      NULL,
    `give_coupons_map`        varchar(255)  NULL,
    `seckill_activity_id`     bigint     NULL,
    `bargain_activity_id`     bigint     NULL,
    `bargain_record_id`       bigint     NULL,
    `combination_activity_id` bigint     NULL,
    `combination_head_id`     bigint     NULL,
    `combination_record_id`   bigint     NULL,
    `creator`                 varchar(255)           DEFAULT '',
    `create_time`             datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                 varchar(255)           DEFAULT '',
    `update_time`             datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_order_item`
(
    `id`                bigint   NOT NULL AUTO_INCREMENT,
    `user_id`           bigint   NOT NULL,
    `order_id`          bigint   NOT NULL,
    `cart_id`           int      NULL,
    `spu_id`            bigint   NOT NULL,
    `spu_name`          varchar(255)  NOT NULL,
    `sku_id`            bigint   NOT NULL,
    `properties`        varchar(255),
    `pic_url`           varchar(255),
    `count`             int      NOT NULL,
    `comment_status`    bit(1)  NULL,
    `price`             int      NOT NULL,
    `discount_price`    int      NOT NULL,
    `delivery_price`    int      NULL,
    `adjust_price`      int      NULL,
    `pay_price`         int      NOT NULL,
    `coupon_price`      int      NULL,
    `point_price`       int      NULL,
    `use_point`         int      NULL,
    `give_point`        int      NULL,
    `vip_price`         int      NULL,
    `after_sale_id`     bigint     NULL,
    `after_sale_status` int      NOT NULL,
    `creator`           varchar(255)           DEFAULT '',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           varchar(255)           DEFAULT '',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_after_sale`
(
    `id`             bigint   NOT NULL AUTO_INCREMENT,
    `no`             varchar(255)  NOT NULL,
    `status`         int      NOT NULL,
    `type`           int      NOT NULL,
    `way`            int      NOT NULL,
    `user_id`        bigint   NOT NULL,
    `apply_reason`   varchar(255)  NOT NULL,
    `apply_description` varchar(255),
    `apply_pic_urls` varchar(255),
    `order_id`       bigint   NOT NULL,
    `order_no`       varchar(255)  NOT NULL,
    `order_item_id`  bigint   NOT NULL,
    `spu_id`         bigint   NOT NULL,
    `spu_name`       varchar(255)  NOT NULL,
    `sku_id`         bigint   NOT NULL,
    `properties`     varchar(255),
    `pic_url`        varchar(255),
    `count`          int      NOT NULL,
    `audit_time`     varchar(255),
    `audit_user_id`  bigint,
    `audit_reason`   varchar(255),
    `refund_price`   int      NOT NULL,
    `pay_refund_id`  bigint,
    `refund_time`    varchar(255),
    `logistics_id`   bigint,
    `logistics_no`   varchar(255),
    `delivery_time`  varchar(255),
    `receive_time`   varchar(255),
    `receive_reason` varchar(255),
    `creator`        varchar(255)           DEFAULT '',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        varchar(255)           DEFAULT '',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_after_sale_log`
(
    `id`            bigint   NOT NULL AUTO_INCREMENT,
    `user_id`       bigint   NOT NULL,
    `user_type`     int      NOT NULL,
    `after_sale_id` bigint   NOT NULL,
    `order_id`      bigint   NOT NULL,
    `order_item_id` bigint   NOT NULL,
    `before_status` int,
    `after_status`  int      NOT NULL,
    `content`       varchar(255)  NOT NULL,
    `creator`       varchar(255)           DEFAULT '',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`       varchar(255)           DEFAULT '',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_brokerage_user`
(
    `id`                bigint   NOT NULL AUTO_INCREMENT,
    `bind_user_id`      bigint   NOT NULL,
    `bind_user_time`    varchar(255),
    `brokerage_enabled` bit(1)      NOT NULL,
    `brokerage_time`    varchar(255),
    `price`             int      NOT NULL,
    `frozen_price`      int      NOT NULL,
    `creator`           varchar(255)           DEFAULT '',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`           varchar(255)           DEFAULT '',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id`         bigint   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `trade_brokerage_record`
(
    `id`            int      NOT NULL AUTO_INCREMENT,
    `user_id`       bigint   NOT NULL,
    `biz_id`        varchar(255)  NOT NULL,
    `biz_type`      varchar(255)  NOT NULL,
    `title`         varchar(255)  NOT NULL,
    `price`         int      NOT NULL,
    `total_price`   int      NOT NULL,
    `description`   varchar(255)  NOT NULL,
    `status`        varchar(255)  NOT NULL,
    `frozen_days`   int      NOT NULL,
    `unfreeze_time` varchar(255),
    `creator`       varchar(255)           DEFAULT '',
    `create_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`       varchar(255)           DEFAULT '',
    `update_time`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY (`id`)
);
CREATE TABLE IF NOT EXISTS `trade_brokerage_withdraw`
(
    `id`                  int      NOT NULL AUTO_INCREMENT,
    `user_id`             bigint   NOT NULL,
    `price`               int      NOT NULL,
    `fee_price`           int      NOT NULL,
    `total_price`         int      NOT NULL,
    `type`                varchar(255)  NOT NULL,
    `name`                varchar(255),
    `account_no`          varchar(255),
    `bank_name`           varchar(255),
    `bank_address`        varchar(255),
    `account_qr_code_url` varchar(255),
    `status`              varchar(255)  NOT NULL,
    `audit_reason`        varchar(255),
    `audit_time`          varchar(255),
    `remark`              varchar(255),
    `creator`             varchar(255)           DEFAULT '',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`             varchar(255)           DEFAULT '',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint not null DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_delivery_express`
(
    `id`          int      NOT NULL AUTO_INCREMENT,
    `code`        varchar(255)  NULL,
    `name`        varchar(255),
    `logo`        varchar(255)  NULL,
    `sort`        int      NOT NULL,
    `status`      int      NOT NULL,
    `creator`     varchar(255)           DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)           DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)      NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_delivery_pick_up_store`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `name`           varchar(255) NOT NULL,
    `introduction`   varchar(255)          DEFAULT NULL,
    `phone`          varchar(255)          DEFAULT NULL,
    `area_id`        int                   DEFAULT NULL,
    `detail_address` varchar(255)          DEFAULT NULL,
    `logo`           varchar(255)          DEFAULT NULL,
    `opening_time`   time                  DEFAULT NULL,
    `closing_time`   time                  DEFAULT NULL,
    `latitude`       double                DEFAULT NULL,
    `longitude`      double                DEFAULT NULL,
    `verify_user_ids` varchar(255)         DEFAULT NULL,
    `status`         int          NOT NULL,
    `creator`        varchar(255)          DEFAULT '',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        varchar(255)          DEFAULT '',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        bit(1)       NOT NULL DEFAULT b'0',
    `tenant_id`      bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_delivery_express_template`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `name`        varchar(255) NOT NULL,
    `charge_mode` int          NOT NULL,
    `sort`        int          NOT NULL,
    `creator`     varchar(255)          DEFAULT '',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)          DEFAULT '',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)       NOT NULL DEFAULT b'0',
    `tenant_id`   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_charge`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `template_id` bigint       NOT NULL,
    `area_ids`    varchar(255)          DEFAULT NULL,
    `charge_mode` int          NOT NULL,
    `start_count` double       NOT NULL,
    `start_price` int          NOT NULL,
    `extra_count` double       NOT NULL,
    `extra_price` int          NOT NULL,
    `creator`     varchar(255)          DEFAULT '',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)          DEFAULT '',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)       NOT NULL DEFAULT b'0',
    `tenant_id`   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_free`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `template_id` bigint       NOT NULL,
    `area_ids`    varchar(255)          DEFAULT NULL,
    `free_price`  int          NOT NULL,
    `free_count`  int          NOT NULL,
    `creator`     varchar(255)          DEFAULT '',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(255)          DEFAULT '',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     bit(1)       NOT NULL DEFAULT b'0',
    `tenant_id`   bigint       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `member_trade_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Application id',
  `business_name` varchar(128) NOT NULL COMMENT 'Business name',
  `country` varchar(64) NOT NULL COMMENT 'Country',
  `street` varchar(255) NOT NULL COMMENT 'Street',
  `address2` varchar(255) DEFAULT NULL COMMENT 'Address line 2',
  `city` varchar(64) NOT NULL COMMENT 'City',
  `state` varchar(64) NOT NULL COMMENT 'State',
  `postal_code` varchar(32) NOT NULL COMMENT 'Postal code',
  `business_description` varchar(128) NOT NULL COMMENT 'Business description',
  `website` varchar(255) DEFAULT NULL COMMENT 'Website',
  `portfolio` varchar(255) DEFAULT NULL COMMENT 'Portfolio',
  `instagram` varchar(255) DEFAULT NULL COMMENT 'Instagram',
  `pinterest` varchar(255) DEFAULT NULL COMMENT 'Pinterest',
  `houzz` varchar(255) DEFAULT NULL COMMENT 'Houzz',
  `linkedin` varchar(255) DEFAULT NULL COMMENT 'LinkedIn',
  `primary_email` varchar(255) NOT NULL COMMENT 'Primary email',
  `authorized_users_json` text NOT NULL COMMENT 'Authorized users JSON',
  `business_documents_json` text NOT NULL COMMENT 'Business documents JSON',
  `tax_documents_json` text COMMENT 'Tax documents JSON',
  `email_opt_in` bit(1) DEFAULT b'1' COMMENT 'Email opt-in',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'Status: 0 pending, 1 approved, 2 rejected',
  `trade_id` varchar(64) DEFAULT NULL COMMENT 'Approved Trade ID',
  `review_reason` varchar(512) DEFAULT NULL COMMENT 'Review reason',
  `review_time` datetime DEFAULT NULL COMMENT 'Review time',
  `reviewer_id` bigint DEFAULT NULL COMMENT 'Reviewer user id',
  `creator` varchar(64) DEFAULT '' COMMENT 'Creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `updater` varchar(64) DEFAULT '' COMMENT 'Updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Deleted',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'Tenant id',
  PRIMARY KEY (`id`),
  KEY `idx_member_trade_application_email_status` (`primary_email`, `status`),
  KEY `idx_member_trade_application_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Member Trade Program application';

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (6800, 'Trade Application', '', 2, 8, 2262, 'trade-application', 'ep:document-checked', 'member/trade/application/index', 'MemberTradeApplication', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6801, 'Trade Application Query', 'member:trade-application:query', 3, 1, 6800, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
  (6802, 'Trade Application Review', 'member:trade-application:review', 3, 2, 6800, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

SET FOREIGN_KEY_CHECKS=1;
