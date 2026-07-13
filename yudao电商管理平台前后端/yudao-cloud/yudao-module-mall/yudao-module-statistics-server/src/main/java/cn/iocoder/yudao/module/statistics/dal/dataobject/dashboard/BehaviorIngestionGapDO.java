package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.*;
@TableName("statistics_behavior_ingestion_gap") @KeySequence("statistics_behavior_ingestion_gap_seq")
@Data @EqualsAndHashCode(callSuper=true)
public class BehaviorIngestionGapDO extends TenantBaseDO { @TableId private Long id; private LocalDate day; private String reasonCode; private LocalDateTime bucketStart; private LocalDateTime firstSeenAt; private LocalDateTime lastSeenAt; private Long rejectedCount; }
