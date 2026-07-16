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

@TableName("trade_fulfillment_idempotency")
@KeySequence("trade_fulfillment_idempotency_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentIdempotencyDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private String operation;
    @ToString.Exclude
    private String idempotencyKeyHash;
    @ToString.Exclude
    private String requestHash;
    private String resourceType;
    private Long resourceId;
    private String status;
    private LocalDateTime expiresAt;

}
