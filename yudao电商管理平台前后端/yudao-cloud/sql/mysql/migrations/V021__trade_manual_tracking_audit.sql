-- Append structured manual-event audit fields after the published V018-V020 catalog.
ALTER TABLE `trade_tracking_event`
  ADD COLUMN `manual_operator_id` bigint DEFAULT NULL AFTER `source`,
  ADD COLUMN `manual_reason` varchar(500) DEFAULT NULL AFTER `manual_operator_id`,
  ADD COLUMN `request_trace_id` varchar(64) DEFAULT NULL AFTER `manual_reason`;
