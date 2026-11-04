package cn.iocoder.yudao.module.system.framework.inquiry.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 官网询盘通知配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WebsiteInquiryProperties.class, WebsiteInquirySiteProperties.class})
public class WebsiteInquiryConfiguration {

    @Bean
    public InitializingBean websiteInquiryPropertiesValidator(WebsiteInquiryProperties properties) {
        return properties::validateForStartup;
    }

    @Bean
    public InitializingBean websiteInquirySitePropertiesValidator(
            WebsiteInquiryProperties legacyProperties, WebsiteInquirySiteProperties siteProperties) {
        return () -> siteProperties.validateForStartup(
                legacyProperties.getSharedSecret(), legacyProperties.getTemplateCode());
    }

}
