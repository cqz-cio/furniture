package cn.iocoder.yudao.module.crm.api.inquiry;

import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryRespDTO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
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

    @Override
    public CrmWebsiteInquiryRespDTO getWebsiteInquiry(Long inquiryId) {
        CrmClueDO inquiry = clueService.getClue(inquiryId);
        if (inquiry == null || inquiry.getExternalInquiryId() == null) {
            return null;
        }
        CrmWebsiteInquiryRespDTO result = new CrmWebsiteInquiryRespDTO();
        result.setId(inquiry.getId());
        result.setExternalInquiryId(inquiry.getExternalInquiryId());
        result.setContactName(inquiry.getContactName());
        result.setEmail(inquiry.getEmail());
        result.setCountryCode(inquiry.getCountryCode());
        result.setPhone(inquiry.getTelephone());
        result.setCompanyName(inquiry.getCompanyName());
        result.setSubject(inquiry.getInquirySubject());
        result.setMessage(inquiry.getInquiryMessage());
        result.setSourcePage(inquiry.getSourcePage());
        result.setLocale(inquiry.getLocale());
        result.setUtmSource(inquiry.getUtmSource());
        result.setUtmMedium(inquiry.getUtmMedium());
        result.setUtmCampaign(inquiry.getUtmCampaign());
        result.setSubmittedAt(inquiry.getSubmittedAt());
        return result;
    }

}
