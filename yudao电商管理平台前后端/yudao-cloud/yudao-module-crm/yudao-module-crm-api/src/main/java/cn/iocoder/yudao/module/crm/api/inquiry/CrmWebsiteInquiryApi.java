package cn.iocoder.yudao.module.crm.api.inquiry;

import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import jakarta.validation.Valid;

/**
 * 官网询盘写入 CRM 的模块接口。
 *
 * <p>当前 Oakved 以统一后端运行，因此该接口由 CRM Server 在同一进程内实现，
 * 供 System 模块的公开询盘入口调用。</p>
 */
public interface CrmWebsiteInquiryApi {

    /**
     * 创建官网询盘。同一租户、同一外部询盘编号重复提交时返回原记录。
     */
    CrmWebsiteInquiryCreateRespDTO createWebsiteInquiry(@Valid CrmWebsiteInquiryCreateReqDTO reqDTO);

}
