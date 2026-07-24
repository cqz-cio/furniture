package cn.iocoder.yudao.module.system.framework.inquiry.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 官网询盘通知配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebsiteInquiryProperties.class)
public class WebsiteInquiryConfiguration {
}
