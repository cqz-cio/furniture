-- ERP-managed VANZ enterprise Blog with draft snapshots, public publishing,
-- release history, tenant isolation and secure preview permissions.

CREATE TABLE IF NOT EXISTS `website_blog_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `site_id` bigint NOT NULL,
  `locale` varchar(32) NOT NULL DEFAULT 'en',
  `slug` varchar(140) NOT NULL,
  `legacy_path` varchar(220) NOT NULL DEFAULT '',
  `title` varchar(180) NOT NULL,
  `title_lines_json` json NOT NULL,
  `category` varchar(80) NOT NULL,
  `label` varchar(80) NOT NULL,
  `summary` varchar(600) NOT NULL,
  `cover_image_url` varchar(500) NOT NULL,
  `cover_image_alt` varchar(240) NOT NULL,
  `hero_image_url` varchar(500) NOT NULL DEFAULT '',
  `sections_json` json NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `visible` bit(1) NOT NULL DEFAULT b'1',
  `published_at` datetime DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `seo_title` varchar(180) NOT NULL DEFAULT '',
  `seo_description` varchar(320) NOT NULL DEFAULT '',
  `version` int NOT NULL DEFAULT 1,
  `published_version` int DEFAULT NULL,
  `published_slug` varchar(140) NOT NULL DEFAULT '',
  `published_payload_json` json DEFAULT NULL,
  `last_published_time` datetime DEFAULT NULL,
  `published_by` varchar(64) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_slug` varchar(140) GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN `slug` ELSE NULL END) STORED,
  `published_slug_active` varchar(140) GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' AND `status` = 'PUBLISHED'
            THEN `published_slug` ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_blog_active_slug`
      (`tenant_id`, `site_id`, `locale`, `active_slug`),
  UNIQUE KEY `uk_website_blog_published_slug`
      (`tenant_id`, `site_id`, `locale`, `published_slug_active`),
  KEY `idx_website_blog_admin`
      (`tenant_id`, `site_id`, `locale`, `status`, `sort_order`),
  KEY `idx_website_blog_public`
      (`tenant_id`, `site_id`, `locale`, `status`, `visible`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ERP-managed website Blog articles';

CREATE TABLE IF NOT EXISTS `website_blog_publish_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `published_version` int NOT NULL,
  `slug` varchar(140) NOT NULL,
  `title` varchar(180) NOT NULL,
  `published_at` datetime NOT NULL,
  `published_by` varchar(64) NOT NULL DEFAULT '',
  `snapshot_json` json NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `active_record` tinyint GENERATED ALWAYS AS
      (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_blog_publish_version`
      (`tenant_id`, `article_id`, `published_version`, `active_record`),
  KEY `idx_website_blog_publish_history`
      (`tenant_id`, `article_id`, `published_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Website Blog publication history';

-- Seed the existing VANZ Blog article as the first published ERP record.
SET @vanz_blog_tenant_id = 162;
SET @vanz_blog_site_id = 1;
-- Match the persisted varchar collation explicitly. MySQL 8 defaults session
-- variables to utf8mb4_0900_ai_ci, while the Yudao schema uses unicode_ci.
SET @vanz_blog_slug = _utf8mb4'5-quick-steps-to-double-your-bedroom-space'
  COLLATE utf8mb4_unicode_ci;
SET @vanz_blog_sections = JSON_ARRAY(
  JSON_OBJECT(
    'id', 'plan-before-buying', 'number', '01',
    'title', 'Lay Out Your Furniture... Before You Buy',
    'paragraphs', JSON_ARRAY(
      'In compact bedrooms, the room itself is rarely the only constraint. Furniture that is too large can consume the circulation space and make the whole layout feel crowded. Measure the room carefully, then sketch the position of the bed and the main storage pieces before you start shopping.',
      'If a standard queen bed dominates the floor plan, a full-size bed may be a better option for one sleeper. Decide where a dresser and bedside table can sit, and choose pieces that fit those dimensions instead of forcing existing furniture into the room. A scaled plan or 3D layout can make awkward corners and tight clearances easier to solve before anything is ordered.',
      'Measuring and laying out a room can feel daunting when time is limited. A properly scaled plan lets you see how every piece will fit before it is ordered and helps resolve tight corners, narrow clearances, and unusual angles early in the process.'
    )
  ),
  JSON_OBJECT(
    'id', 'use-light-colours', 'number', '02',
    'title', 'Use Light and Bright Hues',
    'paragraphs', JSON_ARRAY(
      'Dark surfaces absorb light and can make a small bedroom feel more enclosed. Pale colours reflect the natural light already in the room, helping the space appear brighter, cleaner and more generous.',
      'Apply the same principle beyond wall paint. Lighter upholstery, bedding and timber finishes reduce visual weight and create a calmer, more continuous palette throughout the bedroom.'
    )
  ),
  JSON_OBJECT(
    'id', 'choose-the-bed', 'number', '03',
    'title', 'Choose Your Bed Wisely',
    'paragraphs', JSON_ARRAY(
      'The bed is the visual centre of a bedroom, so its height, width and construction have an outsized effect. When wardrobe storage is already sufficient, a low platform bed can reveal more wall above it and make the ceiling feel higher.',
      'Where storage is limited, look for drawers built into the frame or enough clearance to organise items underneath. A smaller mattress size may also improve movement around the room. Slim metal frames and other visually light constructions can feel less bulky than deep timber or fully upholstered beds.'
    )
  ),
  JSON_OBJECT(
    'id', 'maximise-storage', 'number', '04',
    'title', 'Maximize Your Bedroom Storage and Floor Space',
    'paragraphs', JSON_ARRAY(
      'Floor area and bedside surfaces are valuable in a compact room. Replace floor lamps with one ceiling fixture, then use wall-mounted sconces for reading light. This keeps the nightstand clear for books and the smaller objects you use every day.',
      'A bedroom does not always need matching tables on both sides of the bed. If only one fits, balance the opposite side with a narrow shelf or compact desk. Select dressers and nightstands for useful internal storage rather than exterior bulk, and consider placing a dresser inside the wardrobe when its dimensions allow.'
    )
  ),
  JSON_OBJECT(
    'id', 'add-a-mirror', 'number', '05',
    'title', 'A Big Mirror Changes Your Point of View',
    'paragraphs', JSON_ARRAY(
      'A generous mirror reflects daylight and extends the sightline through a small bedroom. A large round mirror above a dresser is an efficient choice when floor space is tight.',
      'If there is a clear wall available, a full-length floor mirror can create an even stronger sense of depth. Position it where it can catch natural light without obstructing the path around the bed.'
    )
  )
);

INSERT INTO `website_blog_article` (
  `site_id`, `locale`, `slug`, `legacy_path`, `title`, `title_lines_json`,
  `category`, `label`, `summary`, `cover_image_url`, `cover_image_alt`,
  `hero_image_url`, `sections_json`, `status`, `visible`, `published_at`,
  `sort_order`, `seo_title`, `seo_description`, `version`, `published_version`,
  `published_slug`, `last_published_time`, `published_by`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  @vanz_blog_site_id, 'en', @vanz_blog_slug,
  '/5-quick-steps-to-double-your-bedroom-space/',
  '5 Quick Steps to Double Your Bedroom Space',
  JSON_ARRAY('5 Quick Steps to Double Your', 'Bedroom Space'),
  'Bedroom planning', 'Small-space guide',
  'A small bedroom can feel significantly more open when every piece is scaled to the room, light is allowed to travel, and storage is planned before furniture is purchased.',
  '/assets/bedroom.webp',
  'A softly lit bedroom with an upholstered bed, lounge seating, and warm neutral finishes',
  '/assets/bedroom.webp', @vanz_blog_sections,
  'PUBLISHED', b'1', '2020-01-24 09:00:00', 100,
  '5 Quick Steps to Double Your Bedroom Space — VANZ Journal',
  'A small bedroom can feel significantly more open with scaled furniture, brighter finishes and planned storage.',
  1, 1, @vanz_blog_slug, '2020-01-24 09:00:00', 'V045',
  'V045', CURRENT_TIMESTAMP, 'V045', CURRENT_TIMESTAMP, b'0', @vanz_blog_tenant_id
WHERE EXISTS (
  SELECT 1 FROM `system_tenant`
  WHERE `id` = @vanz_blog_tenant_id AND `deleted` = b'0'
)
AND NOT EXISTS (
  SELECT 1 FROM `website_blog_article`
  WHERE `tenant_id` = @vanz_blog_tenant_id
    AND `site_id` = @vanz_blog_site_id
    AND `locale` = 'en'
    AND `slug` = @vanz_blog_slug
    AND `deleted` = b'0'
);

SET @vanz_blog_article_id = (
  SELECT MIN(`id`) FROM `website_blog_article`
  WHERE `tenant_id` = @vanz_blog_tenant_id
    AND `site_id` = @vanz_blog_site_id
    AND `locale` = 'en'
    AND `slug` = @vanz_blog_slug
    AND `deleted` = b'0'
);

SET @vanz_blog_payload = JSON_OBJECT(
  'id', @vanz_blog_article_id,
  'slug', @vanz_blog_slug,
  'path', '/5-quick-steps-to-double-your-bedroom-space/',
  'title', '5 Quick Steps to Double Your Bedroom Space',
  'titleLines', JSON_ARRAY('5 Quick Steps to Double Your', 'Bedroom Space'),
  'category', 'Bedroom planning',
  'label', 'Small-space guide',
  'summary', 'A small bedroom can feel significantly more open when every piece is scaled to the room, light is allowed to travel, and storage is planned before furniture is purchased.',
  'coverImage', JSON_OBJECT(
    'url', '/assets/bedroom.webp',
    'alt', 'A softly lit bedroom with an upholstered bed, lounge seating, and warm neutral finishes'
  ),
  'heroImage', '/assets/bedroom.webp',
  'publishedAt', '2020-01-24T09:00:00',
  'displayDate', 'January 24, 2020',
  'readTime', '5 min read',
  'sortOrder', 100,
  'sections', JSON_EXTRACT(@vanz_blog_sections, '$'),
  'seoTitle', '5 Quick Steps to Double Your Bedroom Space — VANZ Journal',
  'seoDescription', 'A small bedroom can feel significantly more open with scaled furniture, brighter finishes and planned storage.'
);

UPDATE `website_blog_article`
SET `published_payload_json` = @vanz_blog_payload,
    `updater` = 'V045',
    `update_time` = CURRENT_TIMESTAMP
WHERE `id` = @vanz_blog_article_id
  AND `tenant_id` = @vanz_blog_tenant_id
  AND (
    `published_payload_json` IS NULL
    OR JSON_TYPE(JSON_EXTRACT(`published_payload_json`, '$.sections')) = 'STRING'
  )
  AND `deleted` = b'0';

UPDATE `website_blog_publish_record`
SET `snapshot_json` = @vanz_blog_payload,
    `updater` = 'V045',
    `update_time` = CURRENT_TIMESTAMP
WHERE `tenant_id` = @vanz_blog_tenant_id
  AND `article_id` = @vanz_blog_article_id
  AND `published_version` = 1
  AND JSON_TYPE(JSON_EXTRACT(`snapshot_json`, '$.sections')) = 'STRING'
  AND `deleted` = b'0';

INSERT INTO `website_blog_publish_record` (
  `article_id`, `published_version`, `slug`, `title`, `published_at`,
  `published_by`, `snapshot_json`, `creator`, `create_time`, `updater`,
  `update_time`, `deleted`, `tenant_id`
)
SELECT @vanz_blog_article_id, 1, @vanz_blog_slug,
       '5 Quick Steps to Double Your Bedroom Space', '2020-01-24 09:00:00',
       'V045', @vanz_blog_payload, 'V045', CURRENT_TIMESTAMP,
       'V045', CURRENT_TIMESTAMP, b'0', @vanz_blog_tenant_id
WHERE @vanz_blog_article_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `website_blog_publish_record`
    WHERE `tenant_id` = @vanz_blog_tenant_id
      AND `article_id` = @vanz_blog_article_id
      AND `published_version` = 1
      AND `deleted` = b'0'
  );

-- Register the page under 官网运营 (the SEO route group) and grant VANZ roles.
DROP TEMPORARY TABLE IF EXISTS `website_blog_menu_guard`;
CREATE TEMPORARY TABLE `website_blog_menu_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_website_blog_menu_guard` CHECK (`valid` = 1)
) ENGINE=InnoDB;

SET @website_blog_seo_root_id = (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `path` = '/seo' AND `type` = 1 AND `deleted` = b'0'
);

INSERT INTO `website_blog_menu_guard` (`valid`)
SELECT 0 WHERE @website_blog_seo_root_id IS NULL;

START TRANSACTION;

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT
  '企业日志管理','',2,4,@website_blog_seo_root_id,'blog','ep:notebook',
  'seo/blog/index','SeoBlog',0,b'1',b'1',b'1',
  'V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_seo_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @website_blog_seo_root_id
      AND `path` = 'blog' AND `deleted` = b'0'
  );

SET @website_blog_menu_id = (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = @website_blog_seo_root_id
    AND `path` = 'blog' AND `deleted` = b'0'
);

UPDATE `system_menu`
SET `sort` = 5, `updater` = 'V045', `update_time` = CURRENT_TIMESTAMP
WHERE `parent_id` = @website_blog_seo_root_id
  AND `path` = 'analysis' AND `deleted` = b'0';

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志查询','seo:blog:query',3,1,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志创建','seo:blog:create',3,2,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志保存','seo:blog:update',3,3,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志删除','seo:blog:delete',3,4,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:delete' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志预览','seo:blog:preview',3,5,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:preview' AND `deleted` = b'0');

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '日志发布','seo:blog:publish',3,6,@website_blog_menu_id,'','','','',
  0,b'1',b'1',b'1','V045',CURRENT_TIMESTAMP,'V045',CURRENT_TIMESTAMP,b'0'
WHERE @website_blog_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
    WHERE `permission` = 'seo:blog:publish' AND `deleted` = b'0');

DROP TEMPORARY TABLE IF EXISTS `website_blog_menu_scope`;
CREATE TEMPORARY TABLE `website_blog_menu_scope` (
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB;

INSERT INTO `website_blog_menu_scope` (`menu_id`)
SELECT @website_blog_menu_id
UNION SELECT `id` FROM `system_menu`
  WHERE `permission` IN (
    'seo:blog:query','seo:blog:create','seo:blog:update',
    'seo:blog:delete','seo:blog:preview','seo:blog:publish'
  ) AND `deleted` = b'0';

INSERT INTO `website_blog_menu_guard` (`valid`)
SELECT 0 WHERE (SELECT COUNT(*) FROM `website_blog_menu_scope`) <> 7;

DROP TEMPORARY TABLE IF EXISTS `website_blog_package_update`;
CREATE TEMPORARY TABLE `website_blog_package_update` (
  `package_id` bigint NOT NULL,
  `menu_ids` varchar(8192) NOT NULL,
  PRIMARY KEY (`package_id`)
) ENGINE=InnoDB;

INSERT INTO `website_blog_package_update` (`package_id`, `menu_ids`)
SELECT package.`id`,
       CAST(JSON_MERGE_PRESERVE(
         CAST(package.`menu_ids` AS JSON), JSON_ARRAYAGG(scope.`menu_id`)
       ) AS CHAR CHARACTER SET utf8mb4)
FROM `system_tenant` AS tenant
INNER JOIN `system_tenant_package` AS package ON package.`id` = tenant.`package_id`
CROSS JOIN `website_blog_menu_scope` AS scope
WHERE tenant.`id` = @vanz_blog_tenant_id
  AND tenant.`deleted` = b'0'
  AND package.`deleted` = b'0'
  AND JSON_CONTAINS(CAST(package.`menu_ids` AS JSON), CAST(scope.`menu_id` AS JSON), '$') = 0
GROUP BY package.`id`, package.`menu_ids`;

UPDATE `system_tenant_package` AS package
INNER JOIN `website_blog_package_update` AS package_update
  ON package_update.`package_id` = package.`id`
SET package.`menu_ids` = package_update.`menu_ids`,
    package.`updater` = 'V045',
    package.`update_time` = CURRENT_TIMESTAMP;

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.`id`, scope.`menu_id`, 'V045', CURRENT_TIMESTAMP,
       'V045', CURRENT_TIMESTAMP, b'0', @vanz_blog_tenant_id
FROM `system_role` AS role
CROSS JOIN `website_blog_menu_scope` AS scope
WHERE role.`tenant_id` = @vanz_blog_tenant_id
  AND role.`code` IN ('tenant_admin', 'mall_operator')
  AND role.`type` = 1
  AND role.`status` = 0
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` AS existing
    WHERE existing.`role_id` = role.`id`
      AND existing.`menu_id` = scope.`menu_id`
      AND existing.`tenant_id` = @vanz_blog_tenant_id
      AND existing.`deleted` = b'0'
  );

COMMIT;

DROP TEMPORARY TABLE `website_blog_package_update`;
DROP TEMPORARY TABLE `website_blog_menu_scope`;
DROP TEMPORARY TABLE `website_blog_menu_guard`;
