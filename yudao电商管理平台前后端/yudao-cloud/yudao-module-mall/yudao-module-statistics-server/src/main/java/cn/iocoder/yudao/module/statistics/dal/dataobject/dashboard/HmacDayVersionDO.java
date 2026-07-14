package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.*;
@TableName("statistics_dashboard_hmac_day") @KeySequence("statistics_dashboard_hmac_day_seq")
@Data @EqualsAndHashCode(callSuper=true)
public class HmacDayVersionDO extends TenantBaseDO { @TableId private Long id; private LocalDate day; private Integer hashKeyVersion; private LocalDateTime activatedAt; private LocalDateTime destroyAfter; }
