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

@TableName("trade_order_fulfillment_summary")
@KeySequence("trade_order_fulfillment_summary_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFulfillmentSummaryDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private Long orderId;
    private String status;
    private Integer shipmentCount;
    private Integer deliveredShipmentCount;
    private Integer version;

}
