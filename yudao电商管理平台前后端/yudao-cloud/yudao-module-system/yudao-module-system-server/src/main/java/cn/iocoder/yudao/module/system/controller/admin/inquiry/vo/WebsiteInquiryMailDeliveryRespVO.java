package cn.iocoder.yudao.module.system.controller.admin.inquiry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 官网询盘邮件投递状态 Response VO")
@Data
public class WebsiteInquiryMailDeliveryRespVO {

    private Long id;
    private Long inquiryId;
    private String recipientEmail;
    private String customerEmail;
    private Integer status;
    private Integer attemptCount;
    private Long mailLogId;
    private LocalDateTime nextRetryTime;
    private LocalDateTime sentTime;
    private String lastError;
    private LocalDateTime updateTime;

}
