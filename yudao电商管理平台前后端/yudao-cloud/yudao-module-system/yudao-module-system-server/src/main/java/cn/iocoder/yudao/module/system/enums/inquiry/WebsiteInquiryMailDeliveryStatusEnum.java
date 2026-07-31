package cn.iocoder.yudao.module.system.enums.inquiry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 官网询盘邮件投递状态。
 */
@Getter
@AllArgsConstructor
public enum WebsiteInquiryMailDeliveryStatusEnum {

    PENDING(0),
    SENDING(10),
    SUCCESS(20),
    FAILURE(30),
    CONFIG_REQUIRED(40);

    private final Integer status;

}
