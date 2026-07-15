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

@TableName("trade_tracking_status_mapping")
@KeySequence("trade_tracking_status_mapping_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingStatusMappingDO extends BaseDO {

    private Long id;
    private Long tenantId;
    private String providerCode;
    private String carrierCode;
    private String providerStatusNormalized;
    private String standardStatus;
    private String mappingVersion;
    private LocalDateTime effectiveAt;

}
