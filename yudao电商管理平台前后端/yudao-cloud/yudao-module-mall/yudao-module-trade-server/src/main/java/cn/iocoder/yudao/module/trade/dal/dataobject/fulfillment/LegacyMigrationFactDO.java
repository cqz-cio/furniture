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

@TableName("trade_fulfillment_legacy_migration_fact")
@KeySequence("trade_fulfillment_legacy_migration_fact_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyMigrationFactDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private Long orderId;
    private String originCountry;
    private String destinationCountry;
    private String originTimezone;
    private String destinationTimezone;
    private Long warehouseId;
    private Long migrationProviderId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    @ToString.Exclude
    private String sourceReference;

}
