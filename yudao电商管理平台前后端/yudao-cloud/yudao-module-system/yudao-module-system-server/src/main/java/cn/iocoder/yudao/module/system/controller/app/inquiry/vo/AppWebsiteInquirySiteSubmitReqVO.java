package cn.iocoder.yudao.module.system.controller.app.inquiry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户 App - 官网询盘提交 Request VO（独立站点隔离端点）。
 *
 * <p>与旧 {@link AppWebsiteInquirySubmitReqVO} 分开：本 VO 对应 T1 站点隔离协议，
 * 额外携带 {@code category} 与 {@code inquiryType}，且不含国家/电话区号等旧字段。</p>
 *
 * <p>站点键、共享密钥与租户编号均通过请求头解析，绝不出现在请求体中；任何客户端
 * 尝试在 body 里夹带 {@code tenantId}/{@code site}/{@code secret} 等字段都会被忽略。</p>
 */
@Schema(description = "用户 App - 官网询盘提交 Request VO（独立站点隔离）")
@Data
public class AppWebsiteInquirySiteSubmitReqVO {

    @Schema(description = "网页生成的询盘幂等编号（必填，重试复用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2e82ae0d-5db2-4e94-bda0-0db994493885")
    @NotBlank(message = "询盘编号不能为空")
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

    @Schema(description = "公司名称", example = "Northstar Interiors")
    @Size(max = 80, message = "公司名称不能超过 80 个字符")
    private String companyName;

    @Schema(description = "询盘主题", requiredMode = Schema.RequiredMode.REQUIRED, example = "Hotel dining chair project")
    @NotBlank(message = "询盘主题不能为空")
    @Size(max = 100, message = "询盘主题不能超过 100 个字符")
    private String subject;

    @Schema(description = "询盘内容（多行保留）")
    @Size(max = 4000, message = "询盘内容不能超过 4000 个字符")
    private String message;

    @Schema(description = "产品兴趣类别", example = "Cups & Lids")
    @Size(max = 60, message = "类别不能超过 60 个字符")
    private String category;

    @Schema(description = "询盘类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "quote")
    @NotBlank(message = "询盘类型不能为空")
    @Pattern(regexp = "quote|samples|custom|catalog", message = "询盘类型不正确")
    private String inquiryType;

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
