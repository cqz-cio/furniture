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

@TableName("trade_shipment_item")
@KeySequence("trade_shipment_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentItemDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private Long shipmentId;
    private Long orderItemId;
    private Long skuId;
    private BigDecimal quantity;

}
