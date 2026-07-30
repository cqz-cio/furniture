package cn.iocoder.yudao.module.crm.api.inquiry;

import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.crm.service.clue.CrmClueService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 官网询盘写入 CRM 的模块实现。
 */
@Service
@Validated
public class CrmWebsiteInquiryApiImpl implements CrmWebsiteInquiryApi {

    @Resource
    private CrmClueService clueService;

    @Override
    public CrmWebsiteInquiryCreateRespDTO createWebsiteInquiry(CrmWebsiteInquiryCreateReqDTO reqDTO) {
        return clueService.createWebsiteInquiry(reqDTO);
    }

}
