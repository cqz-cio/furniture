package cn.iocoder.yudao.module.system.mq.consumer.mail;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.mq.message.mail.MailSendMessage;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import cn.iocoder.yudao.module.system.service.mail.MailSendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 针对 {@link MailSendMessage} 的消费者
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class MailSendConsumer {

    @Resource
    private MailSendService mailSendService;
    @Resource
    private WebsiteInquiryMailService websiteInquiryMailService;

    @EventListener
    @Async // Spring Event 默认在 Producer 发送的线程，通过 @Async 实现异步
    public void onMessage(MailSendMessage message) {
        log.info("[onMessage][开始发送邮件，日志编号({})]", message.getLogId());
        mailSendService.doSendMail(message);
        if (message.getWebsiteInquiryDeliveryId() != null
                && message.getWebsiteInquiryTenantId() != null) {
            TenantUtils.execute(message.getWebsiteInquiryTenantId(),
                    () -> websiteInquiryMailService.onMailFinished(
                            message.getWebsiteInquiryDeliveryId(), message.getLogId()));
        }
    }

}
