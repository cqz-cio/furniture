CREATE TABLE IF NOT EXISTS `trade_tracking_status_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(32) NOT NULL,
  `carrier_code` varchar(32) NOT NULL,
  `provider_status_normalized` varchar(128) NOT NULL,
  `standard_status` varchar(32) NOT NULL,
  `mapping_version` varchar(32) NOT NULL,
  `effective_at` datetime(6) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tracking_status_mapping` (`tenant_id`,`provider_code`,`carrier_code`,`provider_status_normalized`,`mapping_version`,`deleted`),
  KEY `idx_tracking_status_mapping_effective` (`tenant_id`,`provider_code`,`carrier_code`,`effective_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `trade_tracking_event`
  ADD COLUMN `provider_status_normalized` varchar(128) NOT NULL DEFAULT '' AFTER `provider_status`,
  ADD COLUMN `mapping_version` varchar(32) DEFAULT NULL AFTER `provider_status_normalized`,
  ADD COLUMN `mapping_effective_at` datetime(6) DEFAULT NULL AFTER `mapping_version`,
  ADD COLUMN `mapping_known` bit(1) NOT NULL DEFAULT b'0' AFTER `mapping_effective_at`,
  ADD COLUMN `transition_decision` varchar(20) NOT NULL DEFAULT 'TIMELINE_ONLY' AFTER `mapping_known`,
  ADD COLUMN `previous_status` varchar(32) DEFAULT NULL AFTER `transition_decision`,
  ADD COLUMN `result_status` varchar(32) DEFAULT NULL AFTER `previous_status`,
  MODIFY COLUMN `occurred_at` datetime(6) NOT NULL,
  MODIFY COLUMN `received_at` datetime(6) NOT NULL;

ALTER TABLE `trade_shipment`
  MODIFY COLUMN `last_event_occurred_at` datetime(6) DEFAULT NULL;

ALTER TABLE `trade_shipment_package`
  ADD COLUMN `last_event_occurred_at` datetime(6) DEFAULT NULL AFTER `status`;

ALTER TABLE `trade_shipment_leg`
  ADD COLUMN `last_event_occurred_at` datetime(6) DEFAULT NULL AFTER `status`;
