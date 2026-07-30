package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.crm.api.inquiry.CrmWebsiteInquiryApi;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteInquiryServiceImplTest extends BaseMockitoUnitTest {

    private static final long TENANT_ID = 162L;
    private static final long CONTACT_USER_ID = 216L;

    @InjectMocks
    private WebsiteInquiryServiceImpl websiteInquiryService;

    @Mock
    private WebsiteInquiryProperties properties;
    @Mock
    private TenantService tenantService;
    @Mock
    private NotifySendService notifySendService;
    @Mock
    private CrmWebsiteInquiryApi crmWebsiteInquiryApi;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNotifyInquiry_success() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSharedSecret()).thenReturn("a-long-server-only-secret");
        when(properties.getTenantId()).thenReturn(TENANT_ID);
        when(properties.getTemplateCode()).thenReturn("vanz_website_inquiry");
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));

        AppWebsiteInquirySubmitReqVO reqVO = createReqVO();
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<CrmWebsiteInquiryCreateReqDTO> inquiryCaptor =
                ArgumentCaptor.forClass(CrmWebsiteInquiryCreateReqDTO.class);
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));
        when(notifySendService.sendSingleNotifyToAdmin(
                org.mockito.ArgumentMatchers.eq(CONTACT_USER_ID),
                org.mockito.ArgumentMatchers.eq("vanz_website_inquiry"),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(9001L);

        Long inquiryId = websiteInquiryService.notifyInquiry("a-long-server-only-secret", reqVO);

        assertEquals(7001L, inquiryId);
        verify(crmWebsiteInquiryApi).createWebsiteInquiry(inquiryCaptor.capture());
        CrmWebsiteInquiryCreateReqDTO inquiry = inquiryCaptor.getValue();
        assertEquals(CONTACT_USER_ID, inquiry.getOwnerUserId());
        assertEquals("Alex Morgan", inquiry.getContactName());
        assertEquals("alex@example.com", inquiry.getEmail());
        assertEquals("Hotel dining chair project", inquiry.getSubject());
        verify(notifySendService).sendSingleNotifyToAdmin(
                org.mockito.ArgumentMatchers.eq(CONTACT_USER_ID),
                org.mockito.ArgumentMatchers.eq("vanz_website_inquiry"),
                paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals("Alex Morgan", params.get("name"));
        assertEquals("+44 7700 900123", params.get("phone"));
        assertEquals("-", params.get("companyName"));
        assertEquals("Hotel dining chair project", params.get("subject"));
        assertEquals("google", params.get("utmSource"));
    }

    @Test
    void testNotifyInquiry_duplicateDoesNotSendAnotherNotification() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSharedSecret()).thenReturn("a-long-server-only-secret");
        when(properties.getTenantId()).thenReturn(TENANT_ID);
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, false));

        Long inquiryId = websiteInquiryService.notifyInquiry(
                "a-long-server-only-secret", createReqVO());

        assertEquals(7001L, inquiryId);
        verify(notifySendService, never()).sendSingleNotifyToAdmin(any(), any(), any());
    }

    @Test
    void testNotifyInquiry_notificationFailureDoesNotLosePersistedInquiry() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSharedSecret()).thenReturn("a-long-server-only-secret");
        when(properties.getTenantId()).thenReturn(TENANT_ID);
        when(properties.getTemplateCode()).thenReturn("vanz_website_inquiry");
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));
        when(notifySendService.sendSingleNotifyToAdmin(any(), any(), any()))
                .thenThrow(new IllegalStateException("notify unavailable"));

        Long inquiryId = websiteInquiryService.notifyInquiry(
                "a-long-server-only-secret", createReqVO());

        assertEquals(7001L, inquiryId);
    }

    @Test
    void testNotifyInquiry_rejectsWrongSecret() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSharedSecret()).thenReturn("expected-secret");

        assertServiceException(
                () -> websiteInquiryService.notifyInquiry("wrong-secret", createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
    }

    @Test
    void testNotifyInquiry_rejectsDifferentTenant() {
        TenantContextHolder.setTenantId(999L);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getSharedSecret()).thenReturn("expected-secret");
        when(properties.getTenantId()).thenReturn(TENANT_ID);

        assertServiceException(
                () -> websiteInquiryService.notifyInquiry("expected-secret", createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
    }

    private static AppWebsiteInquirySubmitReqVO createReqVO() {
        AppWebsiteInquirySubmitReqVO reqVO = new AppWebsiteInquirySubmitReqVO();
        reqVO.setExternalInquiryId("2e82ae0d-5db2-4e94-bda0-0db994493885");
        reqVO.setName(" Alex   Morgan ");
        reqVO.setEmail("alex@example.com");
        reqVO.setCountryCode("+44");
        reqVO.setPhone("7700 900123");
        reqVO.setSubject("Hotel dining chair project");
        reqVO.setMessage("Please quote 120 chairs.");
        reqVO.setSourcePage("/products/dining-room");
        reqVO.setLocale("en-GB");
        reqVO.setUtmSource("google");
        return reqVO;
    }

}
