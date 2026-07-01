ALTER TABLE `product_favorite`
    ADD COLUMN `sku_id` bigint NULL COMMENT 'Product SKU id' AFTER `spu_id`,
    ADD COLUMN `count` int NOT NULL DEFAULT 1 COMMENT 'Favorite quantity' AFTER `sku_id`,
    ADD COLUMN `spu_name` varchar(255) NULL COMMENT 'SPU name snapshot' AFTER `count`,
    ADD COLUMN `pic_url` varchar(512) NULL COMMENT 'Product image snapshot' AFTER `spu_name`,
    ADD COLUMN `price` int NULL COMMENT 'Product price snapshot in cents' AFTER `pic_url`,
    ADD COLUMN `market_price` int NULL COMMENT 'Market price snapshot in cents' AFTER `price`,
    ADD COLUMN `color` varchar(128) NULL COMMENT 'Color snapshot' AFTER `market_price`,
    ADD COLUMN `fabric` varchar(255) NULL COMMENT 'Fabric snapshot' AFTER `color`,
    ADD COLUMN `width` varchar(128) NULL COMMENT 'Width snapshot' AFTER `fabric`,
    ADD COLUMN `delivery` varchar(255) NULL COMMENT 'Delivery note snapshot' AFTER `width`,
    ADD COLUMN `dimensions` varchar(255) NULL COMMENT 'Dimensions snapshot' AFTER `delivery`;

CREATE INDEX `idx_product_favorite_user_spu_sku`
    ON `product_favorite` (`user_id`, `spu_id`, `sku_id`);
