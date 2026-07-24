-- Tenant business mode controls whether inventory management is exposed in the ERP UI.

ALTER TABLE `system_tenant`
  ADD COLUMN `business_mode` varchar(16) NOT NULL DEFAULT 'B2C'
    COMMENT '业务模式：B2C 零售型，B2B 询盘型'
    AFTER `websites`;

UPDATE `system_tenant`
SET `business_mode` = 'B2B'
WHERE `id` = 162;

UPDATE `system_tenant`
SET `business_mode` = 'B2C'
WHERE `id` = 121;
