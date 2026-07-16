INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '履约查询', 'trade:fulfillment:shipment:query', 3, 1, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:shipment:query' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '履约创建', 'trade:fulfillment:shipment:create', 3, 2, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:shipment:create' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '履约修改', 'trade:fulfillment:shipment:update', 3, 3, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:shipment:update' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '履约交运', 'trade:fulfillment:shipment:dispatch', 3, 4, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:shipment:dispatch' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '人工轨迹', 'trade:fulfillment:tracking:manual', 3, 5, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:tracking:manual' AND `deleted` = b'0'
);
