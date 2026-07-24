-- VANZ website inquiry -> ERP notify message integration.

ALTER TABLE `system_notify_message`
  MODIFY COLUMN `template_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL COMMENT '模版参数';

INSERT INTO `system_notify_template`
  (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT
  'VANZ 官网询盘',
  'vanz_website_inquiry',
  'VANZ Website',
  'New VANZ inquiry | Received: {submittedAt} | Name: {name} | Email: {email} | Company: {companyName} | Phone / WhatsApp: {phone} | Subject: {subject} | Message: {message}',
  2,
  '["submittedAt","name","email","companyName","phone","subject","message"]',
  0,
  '由 VANZ 官网询盘表单生成的 ERP 站内信',
  'website-inquiry',
  CURRENT_TIMESTAMP,
  'website-inquiry',
  CURRENT_TIMESTAMP,
  b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `system_notify_template`
  WHERE `code` = 'vanz_website_inquiry'
    AND `deleted` = b'0'
);
