package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySiteSubmitReqVO;
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

    /**
     * 独立站点隔离：按站点键服务端解析站点配置并写入 CRM。
     *
     * <p>站点键、共享密钥与租户上下文（框架 {@code tenant-id} 头）都必须在服务端校验，
     * 任何缺失/未知/未启用/错误密钥/跨租户请求都会在入库前失败。</p>
     *
     * @param siteKey 站点键（来自 {@code X-Site-Inquiry-Site} 请求头）
     * @param sharedSecret 站点共享密钥（来自 {@code X-Site-Inquiry-Secret} 请求头）
     * @param reqVO 询盘内容
     * @return CRM 询盘编号
     */
    Long siteNotifyInquiry(String siteKey, String sharedSecret,
                           @Valid AppWebsiteInquirySiteSubmitReqVO reqVO);

}
