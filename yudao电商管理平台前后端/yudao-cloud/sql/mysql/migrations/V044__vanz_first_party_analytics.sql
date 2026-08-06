-- VANZ tenant 162 first-party consent and B2B behavior analytics.
-- This migration does not enable runtime collection by itself; gateway and
-- statistics-server feature flags and tenant-scoped secrets remain required.

CREATE TABLE IF NOT EXISTS `statistics_consent_evidence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consent_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `policy_version` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evidence_nonce` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `preferences` bit(1) NOT NULL DEFAULT b'0',
  `analytics` bit(1) NOT NULL DEFAULT b'1',
  `marketing` bit(1) NOT NULL DEFAULT b'0',
  `issued_epoch` bigint unsigned NOT NULL,
  `expires_epoch` bigint unsigned NOT NULL,
  `withdrawn_epoch` bigint unsigned DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consent_tenant_id` (`tenant_id`,`consent_id`),
  UNIQUE KEY `uk_consent_tenant_nonce` (`tenant_id`,`evidence_nonce`),
  KEY `idx_consent_tenant_expiry` (`tenant_id`,`expires_epoch`),
  CONSTRAINT `chk_statistics_consent_lifetime`
    CHECK (`expires_epoch` > `issued_epoch`),
  CONSTRAINT `chk_statistics_consent_analytics`
    CHECK (`analytics` = b'1')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Anonymous first-party analytics consent evidence; contains no visitor identity or IP';

ALTER TABLE `statistics_behavior_event`
  ADD COLUMN `channel` varchar(32) DEFAULT NULL AFTER `device_type`,
  ADD COLUMN `utm_source` varchar(100) DEFAULT NULL AFTER `channel`,
  ADD COLUMN `utm_medium` varchar(100) DEFAULT NULL AFTER `utm_source`,
  ADD COLUMN `utm_campaign` varchar(100) DEFAULT NULL AFTER `utm_medium`;

ALTER TABLE `statistics_behavior_event`
  DROP CHECK `chk_statistics_behavior_event_source_type`,
  DROP CHECK `chk_statistics_behavior_event_type`;

ALTER TABLE `statistics_behavior_event`
  ADD CONSTRAINT `chk_statistics_behavior_event_type`
    CHECK (`event_type` IN (1,2,3,4,5,6,7,8,9,10,11,12)),
  ADD CONSTRAINT `chk_statistics_behavior_event_source_type`
    CHECK ((`event_source`=1 AND `event_type` IN (1,2,4,5,6,7,8,9,10,11,12))
      OR (`event_source`=2 AND `event_type`=3));
