package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;

import jakarta.validation.Valid;

/**
 * 官网询盘 Service。
 */
public interface WebsiteInquiryService {

    /**
     * 将官网询盘发送给当前租户的联系人管理员。
     *
     * @param sharedSecret Worker 与 ERP 之间的共享密钥
     * @param reqVO 询盘内容
     * @return 站内信编号
     */
    Long notifyInquiry(String sharedSecret, @Valid AppWebsiteInquirySubmitReqVO reqVO);

}
