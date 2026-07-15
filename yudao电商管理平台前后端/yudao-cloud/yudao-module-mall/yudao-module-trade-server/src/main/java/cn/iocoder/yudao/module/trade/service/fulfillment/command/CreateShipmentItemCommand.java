package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class CreateShipmentItemCommand {

    private Long orderItemId;
    private Long skuId;
    private BigDecimal quantity;

}
