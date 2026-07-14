package cn.iocoder.yudao.module.erp.dal.dataobject.integration;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("mall_erp_sync_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class MallErpSyncLogDO extends BaseDO {
    @TableId
    private Long id;
    private String entityType;
    private Long entityId;
    private String direction;
    private String eventType;
    private String idempotencyKey;
    private String requestSummary;
    private String syncStatus;
    private String lastError;
    private Integer retryCount;
}
