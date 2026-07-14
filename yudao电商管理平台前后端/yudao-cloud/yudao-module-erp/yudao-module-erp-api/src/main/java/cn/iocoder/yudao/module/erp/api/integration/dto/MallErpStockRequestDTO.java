package cn.iocoder.yudao.module.erp.api.integration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class MallErpStockRequestDTO {

    private Long mallSkuId;
    private BigDecimal count;

}
