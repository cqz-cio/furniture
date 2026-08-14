package cn.iocoder.yudao.module.erp.api.integration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * ERP 到 Web 商品映射的全量同步结果。
 */
@Data
@Accessors(chain = true)
public class MallErpSyncSummaryDTO {

    private Integer totalSpus = 0;
    private Integer totalSkus = 0;
    private Integer mappedSkus = 0;
    private Integer newMappings = 0;
    private Integer refreshedMappings = 0;
    private Integer unmappedSkus = 0;
    private Integer failedSkus = 0;
    private Integer noSkuSpus = 0;
    private List<MallErpProductDTO> items = new ArrayList<>();

}
