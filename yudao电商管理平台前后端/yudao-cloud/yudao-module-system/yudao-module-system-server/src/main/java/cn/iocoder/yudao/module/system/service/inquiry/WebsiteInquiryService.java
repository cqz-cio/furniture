package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;

import jakarta.validation.Valid;

/**
 * 官网询盘 Service。
 */
public interface WebsiteInquiryService {

    /**
     * 将官网询盘写入 CRM，并在配置可用时提醒当前租户联系人。
     *
     * @param sharedSecret Worker 与 ERP 之间的共享密钥
     * @param reqVO 询盘内容
     * @return CRM 询盘编号
     */
    Long notifyInquiry(String sharedSecret, @Valid AppWebsiteInquirySubmitReqVO reqVO);

}
