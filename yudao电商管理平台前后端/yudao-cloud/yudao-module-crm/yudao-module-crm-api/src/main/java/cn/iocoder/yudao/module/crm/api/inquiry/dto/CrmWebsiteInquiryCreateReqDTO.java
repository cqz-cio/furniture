package cn.iocoder.yudao.module.crm.api.inquiry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 官网询盘写入 CRM Request DTO。
 */
@Data
public class CrmWebsiteInquiryCreateReqDTO {

    @NotBlank(message = "外部询盘编号不能为空")
    @Size(max = 64, message = "外部询盘编号不能超过 64 个字符")
    private String externalInquiryId;

    @NotNull(message = "处理人不能为空")
    private Long ownerUserId;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 60, message = "联系人姓名不能超过 60 个字符")
    private String contactName;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱不能超过 120 个字符")
    private String email;

    @Size(max = 8, message = "电话区号不能超过 8 个字符")
    private String countryCode;

    @Size(max = 40, message = "电话或 WhatsApp 不能超过 40 个字符")
    private String phone;

    @Size(max = 80, message = "公司名称不能超过 80 个字符")
    private String companyName;

    @NotBlank(message = "询盘主题不能为空")
    @Size(max = 100, message = "询盘主题不能超过 100 个字符")
    private String subject;

    @Size(max = 4000, message = "询盘内容不能超过 4000 个字符")
    private String message;

    @Size(max = 255, message = "提交页面不能超过 255 个字符")
    private String sourcePage;

    @Size(max = 32, message = "浏览器语言不能超过 32 个字符")
    private String locale;

    @Size(max = 100, message = "UTM 来源不能超过 100 个字符")
    private String utmSource;

    @Size(max = 100, message = "UTM 媒介不能超过 100 个字符")
    private String utmMedium;

    @Size(max = 100, message = "UTM 活动不能超过 100 个字符")
    private String utmCampaign;

    @NotNull(message = "提交时间不能为空")
    private LocalDateTime submittedAt;

}
