package cn.iocoder.yudao.module.system.controller.app.inquiry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 官网询盘提交 Request VO")
@Data
public class AppWebsiteInquirySubmitReqVO {

    @Schema(description = "网页生成的询盘幂等编号", example = "2e82ae0d-5db2-4e94-bda0-0db994493885")
    @Size(max = 64, message = "询盘编号不能超过 64 个字符")
    private String externalInquiryId;

    @Schema(description = "联系人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "Alex Morgan")
    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 60, message = "联系人姓名不能超过 60 个字符")
    private String name;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "alex@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱不能超过 120 个字符")
    private String email;

    @Schema(description = "国家或地区电话区号", example = "+44")
    @Size(max = 8, message = "电话区号不能超过 8 个字符")
    private String countryCode;

    @Schema(description = "电话或 WhatsApp", example = "7700 900123")
    @Size(max = 40, message = "电话或 WhatsApp 不能超过 40 个字符")
    private String phone;

    @Schema(description = "公司名称", example = "Northstar Interiors")
    @Size(max = 80, message = "公司名称不能超过 80 个字符")
    private String companyName;

    @Schema(description = "询盘主题", requiredMode = Schema.RequiredMode.REQUIRED, example = "Hotel dining chair project")
    @NotBlank(message = "询盘主题不能为空")
    @Size(max = 100, message = "询盘主题不能超过 100 个字符")
    private String subject;

    @Schema(description = "询盘内容")
    @Size(max = 4000, message = "询盘内容不能超过 4000 个字符")
    private String message;

    @Schema(description = "提交页面", example = "/products/dining-room?utm_source=google")
    @Size(max = 255, message = "提交页面不能超过 255 个字符")
    private String sourcePage;

    @Schema(description = "浏览器语言", example = "en-US")
    @Size(max = 32, message = "浏览器语言不能超过 32 个字符")
    private String locale;

    @Schema(description = "UTM 来源")
    @Size(max = 100, message = "UTM 来源不能超过 100 个字符")
    private String utmSource;

    @Schema(description = "UTM 媒介")
    @Size(max = 100, message = "UTM 媒介不能超过 100 个字符")
    private String utmMedium;

    @Schema(description = "UTM 活动")
    @Size(max = 100, message = "UTM 活动不能超过 100 个字符")
    private String utmCampaign;

}
