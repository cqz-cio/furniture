-- Trendz Green independent site inquiry: persist category and inquiry type from the
-- T1 site-isolated protocol. The legacy VANZ `/notify` path never sets these columns
-- and remains unchanged. Columns are NOT NULL DEFAULT '' to match the existing
-- website-inquiry columns added in V032 (existing rows keep the empty default).
--
-- This file is a migration SOURCE only. It is applied by Flyway when the backend
-- starts; it is NOT applied by this task.

ALTER TABLE `crm_clue`
  ADD COLUMN `category` varchar(60) NOT NULL DEFAULT '' COMMENT '询盘产品兴趣类别' AFTER `inquiry_message`,
  ADD COLUMN `inquiry_type` varchar(16) NOT NULL DEFAULT '' COMMENT '询盘类型：quote/samples/custom/catalog' AFTER `category`;
