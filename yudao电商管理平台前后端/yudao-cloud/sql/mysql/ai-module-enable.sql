-- Repeatable MySQL 8 migration for the JDK 17 AI module.
-- Existing business data is preserved. Existing AI keys and their models are disabled
-- until an operator explicitly verifies or replaces the credentials in the UI.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `api_key` varchar(1024) NOT NULL,
  `platform` varchar(64) NOT NULL,
  `url` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_api_key_platform_status` (`platform`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `model` varchar(128) NOT NULL,
  `platform` varchar(64) NOT NULL,
  `type` tinyint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `temperature` double DEFAULT NULL,
  `max_tokens` int DEFAULT NULL,
  `max_contexts` int DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_model_key_id` (`key_id`),
  KEY `idx_ai_model_type_status_sort` (`type`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_chat_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `category` varchar(64) NOT NULL DEFAULT '',
  `description` varchar(512) DEFAULT NULL,
  `system_message` text,
  `user_id` bigint DEFAULT NULL,
  `model_id` bigint NOT NULL,
  `knowledge_ids` text,
  `tool_ids` text,
  `mcp_client_names` text,
  `public_status` bit(1) NOT NULL DEFAULT b'0',
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_role_user_id` (`user_id`),
  KEY `idx_ai_chat_role_model_id` (`model_id`),
  KEY `idx_ai_chat_role_public_status_sort` (`public_status`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_chat_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(256) NOT NULL DEFAULT '',
  `pinned` bit(1) NOT NULL DEFAULT b'0',
  `pinned_time` datetime DEFAULT NULL,
  `role_id` bigint DEFAULT NULL,
  `model_id` bigint NOT NULL,
  `model` varchar(128) NOT NULL,
  `system_message` text,
  `temperature` double DEFAULT NULL,
  `max_tokens` int DEFAULT NULL,
  `max_contexts` int DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_conversation_user_pinned` (`user_id`, `pinned`),
  KEY `idx_ai_chat_conversation_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `reply_id` bigint DEFAULT NULL,
  `type` varchar(32) NOT NULL,
  `user_id` bigint NOT NULL,
  `role_id` bigint DEFAULT NULL,
  `model` varchar(128) DEFAULT NULL,
  `model_id` bigint DEFAULT NULL,
  `content` longtext,
  `reasoning_content` longtext,
  `use_context` bit(1) NOT NULL DEFAULT b'1',
  `segment_ids` text,
  `web_search_pages` longtext,
  `attachment_urls` longtext,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_message_conversation_id` (`conversation_id`, `id`),
  KEY `idx_ai_chat_message_user_id` (`user_id`),
  KEY `idx_ai_chat_message_reply_id` (`reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `prompt` text NOT NULL,
  `platform` varchar(64) NOT NULL,
  `model_id` bigint NOT NULL,
  `model` varchar(128) NOT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `status` tinyint NOT NULL,
  `finish_time` datetime DEFAULT NULL,
  `error_message` text,
  `pic_url` varchar(2048) DEFAULT NULL,
  `public_status` bit(1) NOT NULL DEFAULT b'0',
  `options` longtext,
  `buttons` longtext,
  `task_id` varchar(128) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_image_user_id` (`user_id`),
  KEY `idx_ai_image_status_platform` (`status`, `platform`),
  KEY `idx_ai_image_model_id` (`model_id`),
  KEY `idx_ai_image_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `embedding_model_id` bigint NOT NULL,
  `embedding_model` varchar(128) NOT NULL,
  `top_k` int NOT NULL DEFAULT 3,
  `similarity_threshold` double NOT NULL DEFAULT 0.65,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_embedding_model_id` (`embedding_model_id`),
  KEY `idx_ai_knowledge_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_id` bigint NOT NULL,
  `name` varchar(256) NOT NULL,
  `url` varchar(2048) DEFAULT NULL,
  `content` longtext,
  `content_length` int NOT NULL DEFAULT 0,
  `tokens` int NOT NULL DEFAULT 0,
  `segment_max_tokens` int NOT NULL DEFAULT 0,
  `retrieval_count` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_document_knowledge_id` (`knowledge_id`),
  KEY `idx_ai_knowledge_document_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_knowledge_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_id` bigint NOT NULL,
  `document_id` bigint NOT NULL,
  `content` longtext NOT NULL,
  `content_length` int NOT NULL DEFAULT 0,
  `vector_id` varchar(256) DEFAULT NULL,
  `tokens` int NOT NULL DEFAULT 0,
  `retrieval_count` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_segment_knowledge_status` (`knowledge_id`, `status`),
  KEY `idx_ai_knowledge_segment_document_id` (`document_id`),
  KEY `idx_ai_knowledge_segment_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_mind_map` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `platform` varchar(64) NOT NULL,
  `model_id` bigint NOT NULL,
  `model` varchar(128) NOT NULL,
  `prompt` text NOT NULL,
  `generated_content` longtext,
  `error_message` text,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_mind_map_user_id` (`user_id`),
  KEY `idx_ai_mind_map_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_name` (`name`),
  KEY `idx_ai_tool_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_music` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(256) DEFAULT NULL,
  `lyric` longtext,
  `image_url` varchar(2048) DEFAULT NULL,
  `audio_url` varchar(2048) DEFAULT NULL,
  `video_url` varchar(2048) DEFAULT NULL,
  `status` tinyint NOT NULL,
  `generate_mode` tinyint NOT NULL,
  `description` text,
  `platform` varchar(64) NOT NULL,
  `model` varchar(128) DEFAULT NULL,
  `tags` text,
  `duration` double DEFAULT NULL,
  `public_status` bit(1) NOT NULL DEFAULT b'0',
  `task_id` varchar(128) DEFAULT NULL,
  `error_message` text,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_music_user_id` (`user_id`),
  KEY `idx_ai_music_status` (`status`),
  KEY `idx_ai_music_public_status` (`public_status`),
  KEY `idx_ai_music_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `code` varchar(128) NOT NULL,
  `graph` longtext NOT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_workflow_code` (`code`),
  KEY `idx_ai_workflow_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_write` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` tinyint NOT NULL,
  `platform` varchar(64) NOT NULL,
  `model_id` bigint NOT NULL,
  `model` varchar(128) NOT NULL,
  `prompt` text,
  `generated_content` longtext,
  `original_content` longtext,
  `length` tinyint DEFAULT NULL,
  `format` tinyint DEFAULT NULL,
  `tone` tinyint DEFAULT NULL,
  `language` tinyint DEFAULT NULL,
  `error_message` text,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_write_user_id` (`user_id`),
  KEY `idx_ai_write_model_id` (`model_id`),
  KEY `idx_ai_write_type_platform` (`type`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS `ai_add_column_if_missing`$$
CREATE PROCEDURE `ai_add_column_if_missing`(
  IN p_table varchar(64), IN p_column varchar(64), IN p_definition text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ai_add_column_if_missing('ai_api_key', 'url', 'varchar(512) DEFAULT NULL');
CALL ai_add_column_if_missing('ai_model', 'type', 'tinyint NOT NULL DEFAULT 1');
CALL ai_add_column_if_missing('ai_model', 'temperature', 'double DEFAULT NULL');
CALL ai_add_column_if_missing('ai_model', 'max_tokens', 'int DEFAULT NULL');
CALL ai_add_column_if_missing('ai_model', 'max_contexts', 'int DEFAULT NULL');
CALL ai_add_column_if_missing('ai_knowledge', 'embedding_model_id', 'bigint NOT NULL DEFAULT 0');
CALL ai_add_column_if_missing('ai_knowledge', 'embedding_model', "varchar(128) NOT NULL DEFAULT ''");
CALL ai_add_column_if_missing('ai_knowledge', 'top_k', 'int NOT NULL DEFAULT 3');
CALL ai_add_column_if_missing('ai_knowledge', 'similarity_threshold', 'double NOT NULL DEFAULT 0.65');
DROP PROCEDURE IF EXISTS `ai_add_column_if_missing`;

-- Keep rows for audit/rollback, but make keyless startup safe.
UPDATE `ai_api_key` SET `status` = 1, `update_time` = CURRENT_TIMESTAMP WHERE `deleted` = b'0';
UPDATE `ai_model` m
JOIN `ai_api_key` k ON k.`id` = m.`key_id`
SET m.`status` = 1, m.`update_time` = CURRENT_TIMESTAMP
WHERE m.`deleted` = b'0' AND k.`status` <> 0;

DELIMITER $$
DROP PROCEDURE IF EXISTS `ai_ensure_dict_type`$$
CREATE PROCEDURE `ai_ensure_dict_type`(IN p_name varchar(100), IN p_type varchar(100))
BEGIN
  IF EXISTS (SELECT 1 FROM system_dict_type WHERE type = p_type LIMIT 1) THEN
    UPDATE system_dict_type SET name = p_name, status = 0, deleted = b'0', deleted_time = NULL
    WHERE type = p_type;
  ELSE
    INSERT INTO system_dict_type(name, type, status, creator, updater, deleted)
    VALUES (p_name, p_type, 0, '1', '1', b'0');
  END IF;
END$$
DROP PROCEDURE IF EXISTS `ai_ensure_dict_data`$$
CREATE PROCEDURE `ai_ensure_dict_data`(
  IN p_sort int, IN p_label varchar(100), IN p_value varchar(100), IN p_type varchar(100), IN p_color varchar(100)
)
BEGIN
  IF EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = p_type AND value = p_value LIMIT 1) THEN
    UPDATE system_dict_data SET sort = p_sort, label = p_label, status = 0,
      color_type = p_color, deleted = b'0'
    WHERE dict_type = p_type AND value = p_value;
  ELSE
    INSERT INTO system_dict_data(sort, label, value, dict_type, status, color_type, css_class, creator, updater, deleted)
    VALUES (p_sort, p_label, p_value, p_type, 0, p_color, '', '1', '1', b'0');
  END IF;
END$$
DELIMITER ;

CALL ai_ensure_dict_type('AI 模型平台', 'ai_platform');
CALL ai_ensure_dict_type('AI 模型类型', 'ai_model_type');
CALL ai_ensure_dict_type('AI 绘画状态', 'ai_image_status');
CALL ai_ensure_dict_type('AI 音乐状态', 'ai_music_status');
CALL ai_ensure_dict_type('AI 音乐生成模式', 'ai_generate_mode');
CALL ai_ensure_dict_type('写作语气', 'ai_write_tone');
CALL ai_ensure_dict_type('写作语言', 'ai_write_language');
CALL ai_ensure_dict_type('写作长度', 'ai_write_length');
CALL ai_ensure_dict_type('写作格式', 'ai_write_format');
CALL ai_ensure_dict_type('AI 写作类型', 'ai_write_type');
CALL ai_ensure_dict_type('AI MCP 客户端名字', 'ai_mcp_client_name');

CALL ai_ensure_dict_data(1, 'OpenAI', 'OpenAI', 'ai_platform', '');
CALL ai_ensure_dict_data(2, 'Ollama', 'Ollama', 'ai_platform', '');
CALL ai_ensure_dict_data(3, '文心一言', 'YiYan', 'ai_platform', '');
CALL ai_ensure_dict_data(4, '讯飞星火', 'XingHuo', 'ai_platform', '');
CALL ai_ensure_dict_data(5, '通义千问', 'TongYi', 'ai_platform', '');
CALL ai_ensure_dict_data(6, 'StableDiffusion', 'StableDiffusion', 'ai_platform', '');
CALL ai_ensure_dict_data(7, 'Midjourney', 'Midjourney', 'ai_platform', '');
CALL ai_ensure_dict_data(8, 'Suno', 'Suno', 'ai_platform', '');
CALL ai_ensure_dict_data(9, 'DeepSeek', 'DeepSeek', 'ai_platform', '');
CALL ai_ensure_dict_data(10, '字节豆包', 'DouBao', 'ai_platform', '');
CALL ai_ensure_dict_data(11, '腾讯混元', 'HunYuan', 'ai_platform', '');
CALL ai_ensure_dict_data(12, '硅基流动', 'SiliconFlow', 'ai_platform', '');
CALL ai_ensure_dict_data(13, '智谱', 'ZhiPu', 'ai_platform', '');
CALL ai_ensure_dict_data(14, 'MiniMax', 'MiniMax', 'ai_platform', '');
CALL ai_ensure_dict_data(15, '月之暗面', 'Moonshot', 'ai_platform', '');
CALL ai_ensure_dict_data(16, '百川智能', 'BaiChuan', 'ai_platform', '');
CALL ai_ensure_dict_data(17, 'Anthropic', 'Anthropic', 'ai_platform', '');
CALL ai_ensure_dict_data(18, '谷歌 Gemini', 'Gemini', 'ai_platform', '');
CALL ai_ensure_dict_data(19, 'OpenAI 微软', 'AzureOpenAI', 'ai_platform', '');
CALL ai_ensure_dict_data(1, '聊天', '1', 'ai_model_type', '');
CALL ai_ensure_dict_data(2, '图像', '2', 'ai_model_type', '');
CALL ai_ensure_dict_data(3, '音频', '3', 'ai_model_type', '');
CALL ai_ensure_dict_data(4, '视频', '4', 'ai_model_type', '');
CALL ai_ensure_dict_data(5, '向量', '5', 'ai_model_type', '');
CALL ai_ensure_dict_data(6, '重排', '6', 'ai_model_type', '');
CALL ai_ensure_dict_data(10, '进行中', '10', 'ai_image_status', 'primary');
CALL ai_ensure_dict_data(20, '已完成', '20', 'ai_image_status', 'success');
CALL ai_ensure_dict_data(30, '已失败', '30', 'ai_image_status', 'warning');
CALL ai_ensure_dict_data(10, '进行中', '10', 'ai_music_status', 'primary');
CALL ai_ensure_dict_data(20, '已完成', '20', 'ai_music_status', 'success');
CALL ai_ensure_dict_data(30, '已失败', '30', 'ai_music_status', 'danger');
CALL ai_ensure_dict_data(1, '歌词模式', '1', 'ai_generate_mode', '');
CALL ai_ensure_dict_data(2, '描述模式', '2', 'ai_generate_mode', '');
CALL ai_ensure_dict_data(1, '自动', '1', 'ai_write_length', '');
CALL ai_ensure_dict_data(2, '短', '2', 'ai_write_length', '');
CALL ai_ensure_dict_data(3, '中等', '3', 'ai_write_length', '');
CALL ai_ensure_dict_data(4, '长', '4', 'ai_write_length', '');
CALL ai_ensure_dict_data(1, '自动', '1', 'ai_write_format', '');
CALL ai_ensure_dict_data(2, '电子邮件', '2', 'ai_write_format', '');
CALL ai_ensure_dict_data(3, '消息', '3', 'ai_write_format', '');
CALL ai_ensure_dict_data(4, '评论', '4', 'ai_write_format', '');
CALL ai_ensure_dict_data(5, '段落', '5', 'ai_write_format', '');
CALL ai_ensure_dict_data(6, '文章', '6', 'ai_write_format', '');
CALL ai_ensure_dict_data(7, '博客文章', '7', 'ai_write_format', '');
CALL ai_ensure_dict_data(8, '想法', '8', 'ai_write_format', '');
CALL ai_ensure_dict_data(9, '大纲', '9', 'ai_write_format', '');
CALL ai_ensure_dict_data(1, '自动', '1', 'ai_write_tone', '');
CALL ai_ensure_dict_data(2, '友善', '2', 'ai_write_tone', '');
CALL ai_ensure_dict_data(3, '随意', '3', 'ai_write_tone', '');
CALL ai_ensure_dict_data(4, '友好', '4', 'ai_write_tone', '');
CALL ai_ensure_dict_data(5, '专业', '5', 'ai_write_tone', '');
CALL ai_ensure_dict_data(6, '诙谐', '6', 'ai_write_tone', '');
CALL ai_ensure_dict_data(7, '有趣', '7', 'ai_write_tone', '');
CALL ai_ensure_dict_data(8, '正式', '8', 'ai_write_tone', '');
CALL ai_ensure_dict_data(1, '自动', '1', 'ai_write_language', '');
CALL ai_ensure_dict_data(2, '中文', '2', 'ai_write_language', '');
CALL ai_ensure_dict_data(3, '英文', '3', 'ai_write_language', '');
CALL ai_ensure_dict_data(4, '韩语', '4', 'ai_write_language', '');
CALL ai_ensure_dict_data(5, '日语', '5', 'ai_write_language', '');
CALL ai_ensure_dict_data(1, '撰写', '1', 'ai_write_type', '');
CALL ai_ensure_dict_data(2, '回复', '2', 'ai_write_type', '');
CALL ai_ensure_dict_data(1, '文件系统', 'filesystem', 'ai_mcp_client_name', '');
DROP PROCEDURE IF EXISTS `ai_ensure_dict_data`;
DROP PROCEDURE IF EXISTS `ai_ensure_dict_type`;

DELIMITER $$
DROP PROCEDURE IF EXISTS `ai_ensure_menu`$$
CREATE PROCEDURE `ai_ensure_menu`(
  IN p_parent bigint, IN p_name varchar(50), IN p_permission varchar(100), IN p_type tinyint,
  IN p_sort int, IN p_path varchar(200), IN p_icon varchar(100),
  IN p_component varchar(255), IN p_component_name varchar(255)
)
BEGIN
  SET @last_ai_menu_id = NULL;
  SELECT id INTO @last_ai_menu_id
  FROM system_menu
  WHERE deleted = b'0' AND (
    (p_permission <> '' AND permission = p_permission) OR
    (p_permission = '' AND parent_id = p_parent AND (name = p_name OR (p_parent = 0 AND path = p_path)))
  )
  ORDER BY id LIMIT 1;

  IF @last_ai_menu_id IS NULL THEN
    INSERT INTO system_menu(name, permission, type, sort, parent_id, path, icon, component, component_name,
      status, visible, keep_alive, always_show, creator, updater, deleted)
    VALUES (p_name, p_permission, p_type, p_sort, p_parent, p_path, p_icon,
      NULLIF(p_component, ''), NULLIF(p_component_name, ''), 0, b'1', b'1', b'1', '1', '1', b'0');
    SET @last_ai_menu_id = LAST_INSERT_ID();
  ELSE
    UPDATE system_menu SET name = p_name, permission = p_permission, type = p_type, sort = p_sort,
      parent_id = p_parent, path = p_path, icon = p_icon,
      component = NULLIF(p_component, ''), component_name = NULLIF(p_component_name, ''),
      status = 0, visible = b'1', deleted = b'0'
    WHERE id = @last_ai_menu_id;
  END IF;
END$$
DELIMITER ;

CALL ai_ensure_menu(0, 'AI 大模型', '', 1, 400, '/ai', 'tabler:ai', '', ''); SET @ai_root = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_root, 'AI 对话', '', 2, 1, 'chat', 'ep:message', 'ai/chat/index/index.vue', 'AiChat');
CALL ai_ensure_menu(@ai_root, 'AI 绘画', '', 2, 2, 'image', 'ep:picture-rounded', 'ai/image/index/index.vue', 'AiImage');
CALL ai_ensure_menu(@ai_root, 'AI 写作', '', 2, 3, 'write', 'fa-solid:book-reader', 'ai/write/index/index.vue', 'AiWrite');
CALL ai_ensure_menu(@ai_root, 'AI 音乐', '', 2, 4, 'music', 'fa:music', 'ai/music/index/index.vue', 'AiMusic');
CALL ai_ensure_menu(@ai_root, 'AI 知识库', '', 2, 5, 'knowledge', 'ep:notebook', 'ai/knowledge/knowledge/index', 'AiKnowledge'); SET @ai_knowledge = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_root, 'AI 工作流', '', 2, 6, 'workflow', 'fa:hand-grab-o', 'ai/workflow/index.vue', 'AiWorkflow'); SET @ai_workflow = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_root, 'AI 思维导图', '', 2, 7, 'mind-map', 'fa:sitemap', 'ai/mindmap/index/index.vue', 'AiMindMap');
CALL ai_ensure_menu(@ai_root, '控制台', '', 1, 100, 'console', 'ep:setting', '', ''); SET @ai_console = @last_ai_menu_id;

CALL ai_ensure_menu(@ai_console, 'API 密钥', '', 2, 1, 'api-key', 'ep:key', 'ai/model/apiKey/index.vue', 'AiApiKey'); SET @ai_api_key_menu = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '模型配置', '', 2, 2, 'model', 'fa-solid:abacus', 'ai/model/model/index.vue', 'AiModel'); SET @ai_model_menu = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '聊天角色', '', 2, 3, 'chat-role', 'fa:user-secret', 'ai/model/chatRole/index.vue', 'AiChatRole'); SET @ai_role_menu = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '工具管理', '', 2, 4, 'tool', 'fa-solid:tools', 'ai/model/tool/index.vue', 'AiTool'); SET @ai_tool_menu = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '聊天管理', '', 2, 10, 'chat-conversation', 'ep:chat-square', 'ai/chat/manager/index.vue', 'AiChatManager'); SET @ai_chat_manage = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '绘画管理', '', 2, 11, 'image', 'fa:file-image-o', 'ai/image/manager/index.vue', 'AiImageManager'); SET @ai_image_manage = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '音乐管理', '', 2, 12, 'music', 'fa:music', 'ai/music/manager/index.vue', 'AiMusicManager'); SET @ai_music_manage = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '写作管理', '', 2, 13, 'write', 'fa:bookmark-o', 'ai/write/manager/index.vue', 'AiWriteManager'); SET @ai_write_manage = @last_ai_menu_id;
CALL ai_ensure_menu(@ai_console, '导图管理', '', 2, 14, 'mind-map', 'fa:map', 'ai/mindmap/manager/index', 'AiMindMapManager'); SET @ai_mind_manage = @last_ai_menu_id;

CALL ai_ensure_menu(@ai_api_key_menu, 'API 密钥查询', 'ai:api-key:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_api_key_menu, 'API 密钥创建', 'ai:api-key:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_api_key_menu, 'API 密钥更新', 'ai:api-key:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_api_key_menu, 'API 密钥删除', 'ai:api-key:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_model_menu, '聊天模型查询', 'ai:model:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_model_menu, '聊天模型创建', 'ai:model:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_model_menu, '聊天模型更新', 'ai:model:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_model_menu, '聊天模型删除', 'ai:model:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_role_menu, '聊天角色查询', 'ai:chat-role:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_role_menu, '聊天角色创建', 'ai:chat-role:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_role_menu, '聊天角色更新', 'ai:chat-role:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_role_menu, '聊天角色删除', 'ai:chat-role:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_tool_menu, '工具查询', 'ai:tool:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_tool_menu, '工具创建', 'ai:tool:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_tool_menu, '工具更新', 'ai:tool:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_tool_menu, '工具删除', 'ai:tool:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_chat_manage, '会话查询', 'ai:chat-conversation:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_chat_manage, '会话删除', 'ai:chat-conversation:delete', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_chat_manage, '消息查询', 'ai:chat-message:query', 3, 11, '', '', '', '');
CALL ai_ensure_menu(@ai_chat_manage, '消息删除', 'ai:chat-message:delete', 3, 12, '', '', '', '');
CALL ai_ensure_menu(@ai_image_manage, '绘画查询', 'ai:image:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_image_manage, '绘图更新', 'ai:image:update', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_image_manage, '绘画删除', 'ai:image:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_music_manage, '音乐查询', 'ai:music:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_music_manage, '音乐更新', 'ai:music:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_music_manage, '音乐删除', 'ai:music:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_write_manage, 'AI 写作查询', 'ai:write:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_write_manage, 'AI 写作删除', 'ai:write:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_mind_manage, '思维导图查询', 'ai:mind-map:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_mind_manage, '思维导图删除', 'ai:mind-map:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_knowledge, 'AI 知识库查询', 'ai:knowledge:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_knowledge, 'AI 知识库创建', 'ai:knowledge:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_knowledge, 'AI 知识库更新', 'ai:knowledge:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_knowledge, 'AI 知识库删除', 'ai:knowledge:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_workflow, 'AI 工作流查询', 'ai:workflow:query', 3, 1, '', '', '', '');
CALL ai_ensure_menu(@ai_workflow, 'AI 工作流创建', 'ai:workflow:create', 3, 2, '', '', '', '');
CALL ai_ensure_menu(@ai_workflow, 'AI 工作流更新', 'ai:workflow:update', 3, 3, '', '', '', '');
CALL ai_ensure_menu(@ai_workflow, 'AI 工作流删除', 'ai:workflow:delete', 3, 4, '', '', '', '');
CALL ai_ensure_menu(@ai_workflow, 'AI 工作流测试', 'ai:workflow:test', 3, 5, '', '', '', '');
DROP PROCEDURE IF EXISTS `ai_ensure_menu`;

INSERT INTO system_role_menu(role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, '1', '1', b'0', r.tenant_id
FROM system_role r
JOIN system_menu m ON m.deleted = b'0' AND (
  m.id = @ai_root OR m.parent_id = @ai_root OR m.permission LIKE 'ai:%' OR
  m.parent_id IN (SELECT id FROM system_menu WHERE parent_id = @ai_console AND deleted = b'0')
)
LEFT JOIN system_role_menu rm ON rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0'
WHERE r.code = 'super_admin' AND r.deleted = b'0' AND rm.id IS NULL;
