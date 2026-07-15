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

@TableName("trade_tracking_event")
@KeySequence("trade_tracking_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingEventDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private Long shipmentId;
    private Long packageId;
    private Long shipmentLegId;
    private Long providerId;
    private String externalEventId;
    private String eventHash;
    private String standardStatus;
    private String providerStatus;
    private String description;
    private String location;
    private LocalDateTime occurredAt;
    private String occurredTimezone;
    private LocalDateTime receivedAt;
    private String rawPayloadRef;
    private String source;

}
