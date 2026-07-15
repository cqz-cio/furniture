package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AddShipmentLegCommand {

    private Long tenantId;
    private Long shipmentId;
    private Long packageId;
    private Integer sequenceNo;
    private String legType;
    private Long carrierId;
    private Long providerId;
    private String serviceLevel;
    @ToString.Exclude
    private String trackingNumber;
    @ToString.Exclude
    private String proNumber;
    @ToString.Exclude
    private String bolNumber;
    @ToString.Exclude
    private String originLocation;
    @ToString.Exclude
    private String destinationLocation;

}
