package cn.iocoder.yudao.module.erp.api.integration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class MallErpProductDTO {

    private Long mallSpuId;
    private Long mallSkuId;
    private Long erpProductId;
    private String erpProductCode;
    private String baseName;
    private BigDecimal costPrice;
    private Boolean enabled;
    private BigDecimal sellableStock;
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private String lastError;

}
