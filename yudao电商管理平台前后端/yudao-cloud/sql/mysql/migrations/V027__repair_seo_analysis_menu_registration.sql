-- Forward-only repair for the SEO analysis menu.
-- V026 is already released and must remain byte-for-byte immutable.

START TRANSACTION;

SET @seo_root_menu_id = (
  SELECT MIN(`id`)
  FROM `system_menu`
  WHERE `path` = '/seo' AND `type` = 1 AND `deleted` = b'0'
);

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT
  '关键词分析','',2,3,@seo_root_menu_id,'analysis','ep:data-analysis','seo/analysis/index','SeoAnalysis',
  0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_root_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `system_menu`
    WHERE `parent_id` = @seo_root_menu_id
      AND `path` = 'analysis'
      AND `deleted` = b'0'
  );

SET @seo_analysis_menu_id = (
  SELECT MIN(`id`)
  FROM `system_menu`
  WHERE `parent_id` = @seo_root_menu_id
    AND `path` = 'analysis'
    AND `deleted` = b'0'
);

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT
  '运行分析','seo:analysis:run',3,1,@seo_analysis_menu_id,'','','','',
  0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_analysis_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `system_menu`
    WHERE `permission` = 'seo:analysis:run' AND `deleted` = b'0'
  );

INSERT INTO `system_menu`
  (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT
  '分析查询','seo:analysis:query',3,2,@seo_analysis_menu_id,'','','','',
  0,b'1',b'1',b'1','seo-migration',NOW(),'seo-migration',NOW(),b'0'
WHERE @seo_analysis_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `system_menu`
    WHERE `permission` = 'seo:analysis:query' AND `deleted` = b'0'
  );

UPDATE `system_menu`
SET `name` = '关键词分析',
    `permission` = '',
    `type` = 2,
    `sort` = 3,
    `icon` = 'ep:data-analysis',
    `component` = 'seo/analysis/index',
    `component_name` = 'SeoAnalysis',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = 'seo-migration',
    `update_time` = NOW()
WHERE `id` = @seo_analysis_menu_id;

UPDATE `system_menu`
SET `name` = '运行分析',
    `type` = 3,
    `sort` = 1,
    `parent_id` = @seo_analysis_menu_id,
    `updater` = 'seo-migration',
    `update_time` = NOW()
WHERE @seo_analysis_menu_id IS NOT NULL
  AND `permission` = 'seo:analysis:run'
  AND `deleted` = b'0';

UPDATE `system_menu`
SET `name` = '分析查询',
    `type` = 3,
    `sort` = 2,
    `parent_id` = @seo_analysis_menu_id,
    `updater` = 'seo-migration',
    `update_time` = NOW()
WHERE @seo_analysis_menu_id IS NOT NULL
  AND `permission` = 'seo:analysis:query'
  AND `deleted` = b'0';

CREATE TEMPORARY TABLE `seo_analysis_menu_registration_guard` (
  `valid` tinyint NOT NULL,
  CONSTRAINT `chk_seo_analysis_menu_registration_guard` CHECK (`valid` = 1)
);

INSERT INTO `seo_analysis_menu_registration_guard` (`valid`)
SELECT CASE
  WHEN @seo_root_menu_id IS NOT NULL
   AND (SELECT COUNT(*) FROM `system_menu`
        WHERE `parent_id` = @seo_root_menu_id
          AND `path` = 'analysis'
          AND `type` = 2
          AND `deleted` = b'0') = 1
   AND (SELECT COUNT(*) FROM `system_menu`
        WHERE `permission` = 'seo:analysis:run'
          AND `parent_id` = @seo_analysis_menu_id
          AND `type` = 3
          AND `deleted` = b'0') = 1
   AND (SELECT COUNT(*) FROM `system_menu`
        WHERE `permission` = 'seo:analysis:query'
          AND `parent_id` = @seo_analysis_menu_id
          AND `type` = 3
          AND `deleted` = b'0') = 1
  THEN 1
  ELSE 0
END;

DROP TEMPORARY TABLE `seo_analysis_menu_registration_guard`;

COMMIT;
