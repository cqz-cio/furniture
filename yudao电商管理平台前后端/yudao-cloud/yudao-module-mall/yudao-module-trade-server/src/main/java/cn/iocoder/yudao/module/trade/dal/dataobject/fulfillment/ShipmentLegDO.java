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

import java.time.LocalDateTime;

@TableName("trade_shipment_leg")
@KeySequence("trade_shipment_leg_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentLegDO extends BaseDO {

    private Long id;
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
    private String status;
    private LocalDateTime lastEventOccurredAt;
    private Integer lastEventStatusPriority;
    private Long lastEventId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer version;

}
