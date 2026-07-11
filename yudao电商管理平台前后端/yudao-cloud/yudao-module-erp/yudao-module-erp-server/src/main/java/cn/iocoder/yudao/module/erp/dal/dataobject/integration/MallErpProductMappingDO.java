package cn.iocoder.yudao.module.erp.dal.dataobject.integration;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("mall_erp_product_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class MallErpProductMappingDO extends BaseDO {
    @TableId
    private Long id;
    private Long mallSpuId;
    private Long mallSkuId;
    private Long erpProductId;
    private String erpProductCode;
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private String lastError;
    private Integer version;
}
