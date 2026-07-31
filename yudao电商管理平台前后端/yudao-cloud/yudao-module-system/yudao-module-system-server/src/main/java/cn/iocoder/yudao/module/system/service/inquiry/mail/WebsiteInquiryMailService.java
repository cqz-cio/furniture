package cn.iocoder.yudao.module.system.service.inquiry.mail;

import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigRespVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailDeliveryRespVO;

import jakarta.validation.Valid;

/**
 * 官网询盘邮件转发 Service。
 */
public interface WebsiteInquiryMailService {

    WebsiteInquiryMailConfigRespVO getConfig();

    void saveConfig(@Valid WebsiteInquiryMailConfigSaveReqVO reqVO);

    Long sendTestMail();

    /**
     * 为已持久化询盘幂等创建投递记录，并在配置可用时发送。
     */
    void ensureDeliveryAndSend(Long inquiryId);

    WebsiteInquiryMailDeliveryRespVO getDelivery(Long inquiryId);

    void resend(Long inquiryId);

    /**
     * 重试当前租户到期且未成功的投递。
     */
    void retryDueDeliveries();

    /**
     * 邮件异步发送完成后的状态回写。
     */
    void onMailFinished(Long deliveryId, Long mailLogId);

}
