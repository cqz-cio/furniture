package cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("trade_shipment_package")
@KeySequence("trade_shipment_package_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentPackageDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private Long shipmentId;
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
    private String status;
    private LocalDateTime lastEventOccurredAt;
    private Integer lastEventStatusPriority;
    private Long lastEventId;
    private Integer version;

}
