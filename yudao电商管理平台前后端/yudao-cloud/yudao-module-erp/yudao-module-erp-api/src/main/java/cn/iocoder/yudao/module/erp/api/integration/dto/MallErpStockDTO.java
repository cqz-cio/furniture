package cn.iocoder.yudao.module.erp.api.integration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class MallErpStockDTO {

    private Long mallSkuId;
    private Long erpProductId;
    private BigDecimal requestedCount;
    private BigDecimal sellableStock;
    private Boolean available;

}
