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

}
