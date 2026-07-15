package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TrackingSnapshot {

    private String carrierCode;
    private String trackingNumber;
    private List<ProviderTrackingEvent> events;

}
