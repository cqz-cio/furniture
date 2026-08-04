package cn.iocoder.yudao.module.system.framework.inquiry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 官网询盘通知配置。
 *
 * 密钥只允许配置在服务端环境变量中，禁止下发到浏览器。
 */
@ConfigurationProperties(prefix = "yudao.website-inquiry")
@Data
public class WebsiteInquiryProperties {

    public static final long VANZ_TENANT_ID = 162L;
    public static final int MIN_SHARED_SECRET_LENGTH = 24;

    /**
     * 是否启用官网询盘通知。
     */
    private boolean enabled;

    /**
     * 允许提交询盘的租户编号。
     */
    private Long tenantId;

    /**
     * Sites Worker 与 ERP 之间使用的共享密钥。
     */
    private String sharedSecret;

    /**
     * 站内信模板编码。
     */
    private String templateCode = "vanz_website_inquiry";

    /**
     * 是否启用询盘邮件失败重试。
     */
    private boolean mailRetryEnabled = true;

    /**
     * 在 Spring 启动阶段校验不可变的 VANZ 租户合同，避免把官网询盘路由到
     * Oakved（121）或其他租户。
     */
    public void validateForStartup() {
        if (tenantId != null && !Long.valueOf(VANZ_TENANT_ID).equals(tenantId)) {
            throw new IllegalStateException("VANZ website inquiry tenant-id must be 162; 121 belongs to Oakved");
        }
        if (!enabled) {
            return;
        }
        if (tenantId == null) {
            throw new IllegalStateException("VANZ website inquiry tenant-id must be configured as 162");
        }
        if (sharedSecret == null || sharedSecret.trim().length() < MIN_SHARED_SECRET_LENGTH) {
            throw new IllegalStateException("VANZ website inquiry shared secret must contain at least 24 characters");
        }
    }

}
