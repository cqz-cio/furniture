package cn.iocoder.yudao.module.system.framework.inquiry.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 官网询盘邮件失败重试任务。
 */
@Component
@Slf4j
public class WebsiteInquiryMailRetryJob {

    @Resource
    private WebsiteInquiryProperties properties;
    @Resource
    private WebsiteInquiryMailService websiteInquiryMailService;

    @Scheduled(fixedDelayString = "${yudao.website-inquiry.mail-retry-interval-ms:60000}")
    public void retry() {
        if (!properties.isMailRetryEnabled() || properties.getTenantId() == null) {
            return;
        }
        try {
            TenantUtils.execute(properties.getTenantId(),
                    websiteInquiryMailService::retryDueDeliveries);
        } catch (RuntimeException ex) {
            log.warn("[retry][官网询盘邮件重试任务执行失败]", ex);
        }
    }

}
