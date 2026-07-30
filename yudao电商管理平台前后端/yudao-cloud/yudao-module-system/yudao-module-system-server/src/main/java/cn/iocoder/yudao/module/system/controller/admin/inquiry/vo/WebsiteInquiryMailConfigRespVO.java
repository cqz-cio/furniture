package cn.iocoder.yudao.module.system.controller.admin.inquiry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 官网询盘邮件转发配置 Response VO")
@Data
public class WebsiteInquiryMailConfigRespVO {

    private Boolean configured;
    private Boolean enabled;
    private String recipientEmail;
    private Long mailAccountId;
    private String senderEmail;
    private String senderName;
    private String subjectTemplate;
    private String contentTemplate;
    private String erpBaseUrl;
    private List<String> availableVariables;
    private LocalDateTime updateTime;

}
