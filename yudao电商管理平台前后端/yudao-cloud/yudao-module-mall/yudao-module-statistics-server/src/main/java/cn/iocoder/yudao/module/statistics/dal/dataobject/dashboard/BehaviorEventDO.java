package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("statistics_behavior_event") @KeySequence("statistics_behavior_event_seq")
@Data @EqualsAndHashCode(callSuper = true)
public class BehaviorEventDO extends TenantBaseDO {
    @TableId private Long id;
    private String eventId;
    private Integer eventType;
    private Integer eventSource;
    private String visitorHash;
    private String sessionHash;
    private Integer hashKeyVersion;
    private Long userId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private String pagePath;
    private String referrerHost;
    private Integer deviceType;
    private String channel;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private Integer trafficQuality;
    private String exclusionReason;
    private LocalDateTime occurredAt;
    private LocalDate eventDay;
}
