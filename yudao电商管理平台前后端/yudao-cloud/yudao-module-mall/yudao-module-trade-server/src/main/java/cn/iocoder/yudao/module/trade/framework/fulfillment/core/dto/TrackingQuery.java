package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TrackingQuery {

    private String carrierCode;
    private String trackingNumber;

}
