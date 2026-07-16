ALTER TABLE `trade_carrier`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_carrier_code`,
  ADD UNIQUE KEY `uk_carrier_code` (`tenant_id`,`code`,`active_record`);

ALTER TABLE `trade_logistics_provider`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_provider_code`,
  ADD UNIQUE KEY `uk_provider_code` (`tenant_id`,`code`,`active_record`);

ALTER TABLE `trade_shipment`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_shipment_no`,
  ADD UNIQUE KEY `uk_shipment_no` (`tenant_id`,`shipment_no`,`active_record`);

ALTER TABLE `trade_shipment_item`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_shipment_item`,
  ADD UNIQUE KEY `uk_shipment_item` (`tenant_id`,`shipment_id`,`order_item_id`,`active_record`);

ALTER TABLE `trade_shipment_package`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_shipment_package_no`,
  DROP INDEX `uk_package_tracking`,
  ADD UNIQUE KEY `uk_shipment_package_no` (`tenant_id`,`shipment_id`,`package_no`,`active_record`),
  ADD UNIQUE KEY `uk_package_tracking` (`tenant_id`,`carrier_id`,`tracking_number`,`active_record`);

ALTER TABLE `trade_shipment_leg`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_shipment_leg_sequence`,
  ADD UNIQUE KEY `uk_shipment_leg_sequence` (`tenant_id`,`shipment_id`,`sequence_no`,`active_record`);

ALTER TABLE `trade_tracking_event`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_tracking_event_external`,
  DROP INDEX `uk_tracking_event_hash`,
  ADD UNIQUE KEY `uk_tracking_event_external` (`tenant_id`,`provider_id`,`external_event_id`,`active_record`),
  ADD UNIQUE KEY `uk_tracking_event_hash` (`tenant_id`,`provider_id`,`event_hash`,`active_record`);

ALTER TABLE `trade_order_fulfillment_summary`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_order_fulfillment_summary`,
  ADD UNIQUE KEY `uk_order_fulfillment_summary` (`tenant_id`,`order_id`,`active_record`);

ALTER TABLE `trade_fulfillment_idempotency`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_fulfillment_idempotency`,
  ADD UNIQUE KEY `uk_fulfillment_idempotency` (`tenant_id`,`operation`,`idempotency_key_hash`,`active_record`);

ALTER TABLE `trade_fulfillment_outbox_event`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_fulfillment_outbox_event_id`,
  ADD UNIQUE KEY `uk_fulfillment_outbox_event_id` (`tenant_id`,`event_id`,`active_record`);

ALTER TABLE `trade_tracking_status_mapping`
  ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  DROP INDEX `uk_tracking_status_mapping`,
  ADD UNIQUE KEY `uk_tracking_status_mapping` (`tenant_id`,`provider_code`,`carrier_code`,`provider_status_normalized`,`mapping_version`,`active_record`);
