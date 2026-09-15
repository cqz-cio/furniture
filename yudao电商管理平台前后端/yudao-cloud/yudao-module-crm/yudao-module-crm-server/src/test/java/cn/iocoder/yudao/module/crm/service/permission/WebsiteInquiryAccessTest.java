package cn.iocoder.yudao.module.crm.service.permission;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteInquiryAccessTest {
    private WebsiteInquiryAccess access;
    private SecurityFrameworkService security;
    private CrmClueMapper mapper;
    private final int clueType = CrmBizTypeEnum.CRM_CLUE.getType();
    private final int read = CrmPermissionLevelEnum.READ.getLevel();
    private final int write = CrmPermissionLevelEnum.WRITE.getLevel();

    @BeforeEach void setup() {
        access = new WebsiteInquiryAccess();
        security = mock(SecurityFrameworkService.class);
        mapper = mock(CrmClueMapper.class);
        ReflectionTestUtils.setField(access, "security", security);
        ReflectionTestUtils.setField(access, "clueMapper", mapper);
        TenantContextHolder.setTenantId(163L);
        when(security.hasPermission("crm:clue:triage")).thenReturn(true);
        CrmClueDO clue = new CrmClueDO().setId(5L).setExternalInquiryId("website-5");
        clue.setTenantId(163L);
        when(mapper.selectById(5L)).thenReturn(clue);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void grantsWebsiteReadAndTriageButNotOwnership() {
        assertTrue(access.canAccess(clueType, 5L, read));
        assertTrue(access.canAccess(clueType, 5L, write));
        assertFalse(access.canAccess(clueType, 5L, CrmPermissionLevelEnum.OWNER.getLevel()));
        assertFalse(access.canAccess(CrmBizTypeEnum.CRM_CUSTOMER.getType(), 5L, read));
        assertTrue(access.isWebsiteOnlyOperator());
    }
    @Test void rejectsAnotherTenantEvenIfMapperReturnsItsRow() {
        mapper.selectById(5L).setTenantId(162L);
        assertFalse(access.canAccess(clueType, 5L, read));
        assertFalse(access.canAccess(clueType, 5L, write));
    }
    @Test void rejectsManualMissingAndUnauthenticatedData() {
        mapper.selectById(5L).setExternalInquiryId(" ");
        assertFalse(access.canAccess(clueType, 5L, read));
        assertFalse(access.canAccess(clueType, 6L, write));
        TenantContextHolder.clear();
        assertFalse(access.canAccess(clueType, 5L, write));
    }
    @Test void existingCrmEditorsKeepTheirNormalPolicy() {
        when(security.hasPermission("crm:clue:update")).thenReturn(true);
        assertFalse(access.isWebsiteOnlyOperator());
        when(security.hasPermission("crm:clue:triage")).thenReturn(false);
        assertFalse(access.canAccess(clueType, 5L, read));
    }
}
