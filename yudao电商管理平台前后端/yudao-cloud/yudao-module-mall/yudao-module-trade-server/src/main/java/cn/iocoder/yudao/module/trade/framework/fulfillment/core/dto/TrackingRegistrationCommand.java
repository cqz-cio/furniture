package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TrackingRegistrationCommand {

    private String carrierCode;
    @ToString.Exclude
    private String trackingNumber;

}
