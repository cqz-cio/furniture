package cn.iocoder.yudao.module.crm.api.inquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 官网询盘邮件渲染所需的持久化快照。
 */
@Data
public class CrmWebsiteInquiryRespDTO {

    private Long id;
    private String externalInquiryId;
    private String contactName;
    private String email;
    private String countryCode;
    private String phone;
    private String companyName;
    private String subject;
    private String message;
    private String sourcePage;
    private String locale;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private LocalDateTime submittedAt;

}
