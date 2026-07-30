package cn.iocoder.yudao.module.system.dal.dataobject.inquiry;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.system.enums.inquiry.WebsiteInquiryMailDeliveryStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 官网询盘邮件投递记录。
 */
@TableName("system_website_inquiry_mail_delivery")
@KeySequence("system_website_inquiry_mail_delivery_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteInquiryMailDeliveryDO extends BaseDO {

    @TableId
    private Long id;
    private Long inquiryId;
    private String externalInquiryId;
    private String recipientEmail;
    private String customerEmail;
    /**
     * @see WebsiteInquiryMailDeliveryStatusEnum
     */
    private Integer status;
    private Integer attemptCount;
    private Long mailLogId;
    private LocalDateTime nextRetryTime;
    private LocalDateTime sentTime;
    private String lastError;

}
