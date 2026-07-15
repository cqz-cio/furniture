package cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "trade_fulfillment_outbox_event", autoResultMap = true)
@KeySequence("trade_fulfillment_outbox_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentOutboxEventDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastErrorCode;

}
