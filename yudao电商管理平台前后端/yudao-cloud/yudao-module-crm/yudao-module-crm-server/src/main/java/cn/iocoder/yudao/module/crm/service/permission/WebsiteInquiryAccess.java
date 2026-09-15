package cn.iocoder.yudao.module.crm.service.permission;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Tenant-wide website inquiry triage, without CRM ownership or customer privileges. */
@Component
public class WebsiteInquiryAccess {
    @Resource private SecurityFrameworkService security;
    @Resource private CrmClueMapper clueMapper;

    public boolean isWebsiteOnlyOperator() {
        return security.hasPermission("crm:clue:triage") && !security.hasPermission("crm:clue:update");
    }

    public boolean canAccess(Integer bizType, Long id, Integer level) {
        if (!Objects.equals(bizType, CrmBizTypeEnum.CRM_CLUE.getType())
                || !(CrmPermissionLevelEnum.isRead(level) || CrmPermissionLevelEnum.isWrite(level))
                || TenantContextHolder.getTenantId() == null
                || !security.hasPermission("crm:clue:triage")) return false;
        CrmClueDO clue = clueMapper.selectById(id);
        return clue != null && Objects.equals(clue.getTenantId(), TenantContextHolder.getTenantId())
                && StrUtil.isNotBlank(clue.getExternalInquiryId());
    }
}
