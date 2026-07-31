package cn.iocoder.yudao.module.system.controller.admin.inquiry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 官网询盘邮件转发配置保存 Request VO")
@Data
public class WebsiteInquiryMailConfigSaveReqVO {

    @Schema(description = "是否启用")
    private Boolean enabled = false;

    @Schema(description = "内部接收邮箱", example = "sales@nbvanz.com")
    @Email(message = "接收邮箱格式不正确")
    @Size(max = 255, message = "接收邮箱不能超过 255 个字符")
    private String recipientEmail;

    @Schema(description = "平台发件邮箱账号编号")
    private Long mailAccountId;

    @Schema(description = "发件人显示名称")
    @NotBlank(message = "发件人显示名称不能为空")
    @Size(max = 255, message = "发件人显示名称不能超过 255 个字符")
    private String senderName;

    @Schema(description = "邮件标题模板")
    @NotBlank(message = "邮件标题模板不能为空")
    @Size(max = 255, message = "邮件标题模板不能超过 255 个字符")
    private String subjectTemplate;

    @Schema(description = "邮件 HTML 正文模板")
    @NotBlank(message = "邮件正文模板不能为空")
    @Size(max = 10000, message = "邮件正文模板不能超过 10000 个字符")
    private String contentTemplate;

    @Schema(description = "ERP 管理端地址", example = "https://erp.example.com")
    @Size(max = 512, message = "ERP 管理端地址不能超过 512 个字符")
    private String erpBaseUrl;

}
