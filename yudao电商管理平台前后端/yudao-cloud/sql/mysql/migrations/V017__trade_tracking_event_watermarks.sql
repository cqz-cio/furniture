ALTER TABLE `trade_tracking_status_mapping`
  ADD COLUMN `status_priority` int NOT NULL DEFAULT 0 AFTER `standard_status`;

ALTER TABLE `trade_tracking_event`
  MODIFY COLUMN `external_event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL;

ALTER TABLE `trade_shipment`
  ADD COLUMN `last_event_status_priority` int DEFAULT NULL AFTER `last_event_occurred_at`,
  ADD COLUMN `last_event_id` bigint DEFAULT NULL AFTER `last_event_status_priority`;

ALTER TABLE `trade_shipment_package`
  ADD COLUMN `last_event_status_priority` int DEFAULT NULL AFTER `last_event_occurred_at`,
  ADD COLUMN `last_event_id` bigint DEFAULT NULL AFTER `last_event_status_priority`;

ALTER TABLE `trade_shipment_leg`
  ADD COLUMN `last_event_status_priority` int DEFAULT NULL AFTER `last_event_occurred_at`,
  ADD COLUMN `last_event_id` bigint DEFAULT NULL AFTER `last_event_status_priority`;
