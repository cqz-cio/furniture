package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("statistics_dashboard_export_audit")
@KeySequence("statistics_dashboard_export_audit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardExportAuditDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String exportType;
    private String filterHash;
    private Integer rowCount;
    private String fileSha256;
    private String result;
    private String failureCode;
}
