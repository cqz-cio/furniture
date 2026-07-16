package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class UpsertPackageCommand {

    private Long tenantId;
    private Long shipmentId;
    private Integer expectedVersion;
    private String packageNo;
    private String packageType;
    private Long carrierId;
    @ToString.Exclude
    private String trackingNumber;
    private BigDecimal weight;
    private String weightUnit;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private String dimensionUnit;

}
