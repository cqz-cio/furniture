package cn.iocoder.yudao.framework.tenant.core.web;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.security.TenantSecurityWebFilter;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantVisitContextInterceptorTest {

    private final TenantProperties properties = new TenantProperties();
    private final SecurityFrameworkService security = mock(SecurityFrameworkService.class);
    private final TenantVisitContextInterceptor interceptor = new TenantVisitContextInterceptor(properties, security);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin-api/seo/page/draft");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private LoginUser login(long tenantId) {
        LoginUser user = new LoginUser().setId(148L).setTenantId(tenantId).setUserType(2);
        SecurityFrameworkUtils.setLoginUser(user, request);
        TenantContextHolder.setTenantId(tenantId);
        properties.setPlatformVisitDeniedTenantIds(Set.of(164L));
        return user;
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void platformSuperAdminCannotVisitProtectedTenant() {
        LoginUser user = login(1L);
        when(security.hasAnyRoles("super_admin")).thenReturn(true);
        request.addHeader("visit-tenant-id", "164");
        ServiceException error = assertThrows(ServiceException.class, () -> interceptor.preHandle(request, response, this));
        assertEquals(403, error.getCode());
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertNull(user.getVisitTenantId());
    }

    @Test
    void ownTenantAccountCanAccessWithOrWithoutMatchingVisitHeader() {
        login(164L);
        assertTrue(interceptor.preHandle(request, response, this));
        request.addHeader("visit-tenant-id", "164");
        assertTrue(interceptor.preHandle(request, response, this));
        assertEquals(164L, TenantContextHolder.getTenantId());
        verifyNoInteractions(security);
    }

    @Test
    void otherTenantsRemainVisitableByPlatformAdminAndContextIsRestored() {
        LoginUser user = login(1L);
        when(security.hasAnyRoles("super_admin")).thenReturn(true);
        request.addHeader("visit-tenant-id", "163");
        assertTrue(interceptor.preHandle(request, response, this));
        assertEquals(163L, TenantContextHolder.getTenantId());
        assertEquals(163L, user.getVisitTenantId());
        interceptor.afterCompletion(request, response, this, null);
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertNull(user.getVisitTenantId());
    }

    @Test
    void ordinaryTenantCannotVisitAnotherTenant() {
        login(163L);
        request.addHeader("visit-tenant-id", "164");
        assertEquals(403, assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, response, this)).getCode());
        verifyNoInteractions(security);
    }

    @Test
    void nonSuperAdminPlatformAccountCannotVisit() {
        login(1L);
        request.addHeader("visit-tenant-id", "163");
        assertEquals(403, assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, response, this)).getCode());
    }

    @Test
    void staleVisitCannotBlockOwnAuthenticationOrTenantList() {
        login(1L);
        properties.setIgnoreVisitUrls(Set.of("/admin-api/system/auth/**", "/admin-api/system/user/profile/**"));
        request.addHeader("visit-tenant-id", "164");
        for (String path : Set.of("/admin-api/system/auth/get-permission-info",
                "/admin-api/system/user/profile/get", "/admin-api/system/tenant/visit-list")) {
            request.setRequestURI(path);
            assertTrue(interceptor.preHandle(request, response, this));
            assertEquals(1L, TenantContextHolder.getTenantId());
        }
        request.setRequestURI("/admin-api/seo/page/draft");
        when(security.hasAnyRoles("super_admin")).thenReturn(true);
        assertThrows(ServiceException.class, () -> interceptor.preHandle(request, response, this));
    }

    @Test
    void anonymousVisitHeaderCannotChangePublicContentTenant() {
        TenantContextHolder.setTenantId(164L);
        request.addHeader("visit-tenant-id", "1");
        assertTrue(interceptor.preHandle(request, response, this));
        assertEquals(164L, TenantContextHolder.getTenantId());
    }

    @Test
    void forgedTenantHeaderIsRejectedBeforeVisitInterceptor() throws Exception {
        login(1L);
        TenantContextHolder.setTenantId(164L);
        request.setServletPath("/admin-api/seo/page/draft");
        request.addHeader("tenant-id", "164");
        request.addHeader("visit-tenant-id", "164");
        TenantSecurityWebFilter filter = new TenantSecurityWebFilter(new WebProperties(), properties, Set.of(),
                mock(GlobalExceptionHandler.class), mock(TenantFrameworkService.class));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }
}
