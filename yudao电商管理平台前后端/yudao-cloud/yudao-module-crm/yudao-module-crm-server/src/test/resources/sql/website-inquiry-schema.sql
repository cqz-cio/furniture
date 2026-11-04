-- 测试专用：仅供 WebsiteInquiryTenantDbTest 的一次性 H2 内存库使用。
CREATE TABLE crm_clue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    external_inquiry_id VARCHAR(64),
    name VARCHAR(255), contact_name VARCHAR(100), company_name VARCHAR(200),
    country_code VARCHAR(16), inquiry_subject VARCHAR(200), inquiry_message VARCHAR(4000),
    category VARCHAR(60) NOT NULL DEFAULT '', inquiry_type VARCHAR(16) NOT NULL DEFAULT '',
    source_page VARCHAR(500), locale VARCHAR(20), utm_source VARCHAR(100),
    utm_medium VARCHAR(100), utm_campaign VARCHAR(100), submitted_at TIMESTAMP,
    process_status INT, processed_at TIMESTAMP, test_data BOOLEAN,
    priority INT, sales_stage INT, first_response_at TIMESTAMP,
    follow_up_status BOOLEAN, contact_last_time TIMESTAMP, contact_last_content VARCHAR(1000),
    contact_next_time TIMESTAMP, owner_user_id BIGINT, transform_status BOOLEAN,
    customer_id BIGINT, contact_id BIGINT,
    mobile VARCHAR(32), telephone VARCHAR(32), qq VARCHAR(64), wechat VARCHAR(64),
    email VARCHAR(128), area_id INT, detail_address VARCHAR(255), industry_id INT,
    level INT, source INT, remark VARCHAR(4000),
    creator VARCHAR(64), updater VARCHAR(64), create_time TIMESTAMP, update_time TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_inquiry_tenant_external UNIQUE (tenant_id, external_inquiry_id, deleted)
);
