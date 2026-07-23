-- Vue Router requires top-level route paths to start with a slash.
-- V013 created the dashboard menu as "dashboard", which prevents the
-- permission router from being mounted after a successful login.
UPDATE `system_menu`
SET `path` = '/dashboard',
    `updater` = 'V024',
    `update_time` = NOW()
WHERE `id` = 7990
  AND `parent_id` = 0
  AND `path` = 'dashboard'
  AND `deleted` = b'0';
