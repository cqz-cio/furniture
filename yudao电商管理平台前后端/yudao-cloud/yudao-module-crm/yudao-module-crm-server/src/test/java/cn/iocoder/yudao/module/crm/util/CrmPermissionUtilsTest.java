package cn.iocoder.yudao.module.crm.util;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmPermissionUtilsTest {

    @Test
    void testIsCrmAdmin_crossTenantVisitSkipsTenantRoleLookup() {
        try (MockedStatic<SecurityFrameworkUtils> securityFrameworkUtils =
                     mockStatic(SecurityFrameworkUtils.class)) {
            securityFrameworkUtils.when(SecurityFrameworkUtils::skipPermissionCheck).thenReturn(true);

            assertTrue(CrmPermissionUtils.isCrmAdmin());
        }
    }

    @Test
    void testIsCrmAdmin_supportsPlatformTenantAndCrmAdministrators() {
        Long userId = 1L;
        PermissionCommonApi permissionApi = mock(PermissionCommonApi.class);
        when(permissionApi.hasAnyRoles(eq(userId), any(String[].class))).thenReturn(success(true));

        try (MockedStatic<SpringUtil> springUtil = mockStatic(SpringUtil.class);
             MockedStatic<SecurityFrameworkUtils> securityFrameworkUtils = mockStatic(SecurityFrameworkUtils.class)) {
            springUtil.when(() -> SpringUtil.getBean(PermissionCommonApi.class)).thenReturn(permissionApi);
            securityFrameworkUtils.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);

            assertTrue(CrmPermissionUtils.isCrmAdmin());
            verify(permissionApi).hasAnyRoles(eq(userId), aryEq(new String[]{
                    RoleCodeEnum.SUPER_ADMIN.getCode(),
                    RoleCodeEnum.TENANT_ADMIN.getCode(),
                    RoleCodeEnum.CRM_ADMIN.getCode()
            }));
        }
    }

}
