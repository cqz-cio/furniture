package cn.iocoder.yudao.module.system.service.inquiry;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.crm.api.inquiry.CrmWebsiteInquiryApi;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateReqDTO;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySiteSubmitReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquirySiteProperties;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_CONFIGURATION_ERROR;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_DISABLED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.WEBSITE_INQUIRY_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteInquirySiteServiceImplTest extends BaseMockitoUnitTest {

    private static final long TENANT_ID = 900001L;
    private static final long CONTACT_USER_ID = 216L;
    private static final String SITE_KEY = "trendzgreen";
    private static final String SECRET = "a-random-server-only-secret-32-chars";

    @InjectMocks
    private WebsiteInquiryServiceImpl websiteInquiryService;

    @Mock
    private WebsiteInquiryProperties properties;
    @Mock
    private WebsiteInquirySiteProperties siteProperties;
    @Mock
    private TenantService tenantService;
    @Mock
    private NotifySendService notifySendService;
    @Mock
    private CrmWebsiteInquiryApi crmWebsiteInquiryApi;
    @Mock
    private WebsiteInquiryMailService websiteInquiryMailService;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testSiteNotifyInquiry_success_persistsMappedFieldsAndNoDefaultNotify() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));

        Long inquiryId = websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO());

        assertEquals(7001L, inquiryId);
        ArgumentCaptor<CrmWebsiteInquiryCreateReqDTO> captor =
                ArgumentCaptor.forClass(CrmWebsiteInquiryCreateReqDTO.class);
        verify(crmWebsiteInquiryApi).createWebsiteInquiry(captor.capture());
        CrmWebsiteInquiryCreateReqDTO dto = captor.getValue();
        assertEquals(CONTACT_USER_ID, dto.getOwnerUserId());
        assertEquals("2e82ae0d-5db2-4e94-bda0-0db994493885", dto.getExternalInquiryId());
        assertEquals("Alex Morgan", dto.getContactName());
        assertEquals("alex@example.com", dto.getEmail());
        assertEquals("Northstar Interiors", dto.getCompanyName());
        assertEquals("Hotel dining chair project", dto.getSubject());
        assertEquals("Please quote 120 chairs.\nLine 2", dto.getMessage());
        assertEquals("Dining Chairs", dto.getCategory());
        assertEquals("quote", dto.getInquiryType());
        assertEquals("google", dto.getUtmSource());
        // 默认关闭：不建立站内信与邮件旁路。
        verify(notifySendService, never()).sendSingleNotifyToAdmin(any(), any(), any());
        verify(websiteInquiryMailService, never()).ensureDeliveryAndSend(any());
    }

    @Test
    void testSiteNotifyInquiry_unknownSite_rejected() {
        when(siteProperties.getSite("unknown")).thenReturn(null);

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry("unknown", SECRET, createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_disabledSite_rejected() {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(disabledSite());

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO()),
                WEBSITE_INQUIRY_DISABLED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_wrongSecret_rejected() {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, "a-different-wrong-secret", createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_crossSiteSecret_rejected() {
        // 站点 B 的密钥不能用于访问站点 A。
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, "site-b-server-only-secret-32-chars",
                        createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_forgedTenant_rejected() {
        TenantContextHolder.setTenantId(999999L);
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_missingTenantContext_rejected() {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO()),
                WEBSITE_INQUIRY_UNAUTHORIZED);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_reservedTenant_rejected() {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(162L, SECRET));

        assertServiceException(
                () -> websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO()),
                WEBSITE_INQUIRY_CONFIGURATION_ERROR);
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void testSiteNotifyInquiry_duplicateDoesNotNotifyAgain() {
        TenantContextHolder.setTenantId(TENANT_ID);
        WebsiteInquirySiteProperties.Site site = enabledSite(TENANT_ID, SECRET);
        site.setNotifyEnabled(true);
        site.setTemplateCode("trendzgreen_website_inquiry");
        when(siteProperties.getSite(SITE_KEY)).thenReturn(site);
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, false));

        Long inquiryId = websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO());

        assertEquals(7001L, inquiryId);
        verify(notifySendService, never()).sendSingleNotifyToAdmin(any(), any(), any());
        verify(websiteInquiryMailService, never()).ensureDeliveryAndSend(any());
    }

    @Test
    void testSiteNotifyInquiry_notifyFailureDoesNotLosePersistedInquiry() {
        TenantContextHolder.setTenantId(TENANT_ID);
        WebsiteInquirySiteProperties.Site site = enabledSite(TENANT_ID, SECRET);
        site.setNotifyEnabled(true);
        site.setTemplateCode("trendzgreen_website_inquiry");
        when(siteProperties.getSite(SITE_KEY)).thenReturn(site);
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));
        when(notifySendService.sendSingleNotifyToAdmin(any(), any(), any()))
                .thenThrow(new IllegalStateException("notify unavailable"));

        Long inquiryId = websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO());

        assertEquals(7001L, inquiryId);
    }

    @Test
    void testSiteNotifyInquiry_mailFailureDoesNotLosePersistedInquiry() {
        TenantContextHolder.setTenantId(TENANT_ID);
        WebsiteInquirySiteProperties.Site site = enabledSite(TENANT_ID, SECRET);
        site.setMailEnabled(true);
        when(siteProperties.getSite(SITE_KEY)).thenReturn(site);
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));
        doThrow(new IllegalStateException("mail unavailable"))
                .when(websiteInquiryMailService).ensureDeliveryAndSend(7001L);

        Long inquiryId = websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO());

        assertEquals(7001L, inquiryId);
        verify(websiteInquiryMailService).ensureDeliveryAndSend(7001L);
    }

    @Test
    void testSiteNotifyInquiry_mailEnabledDoesQueueDelivery() {
        TenantContextHolder.setTenantId(TENANT_ID);
        WebsiteInquirySiteProperties.Site site = enabledSite(TENANT_ID, SECRET);
        site.setMailEnabled(true);
        when(siteProperties.getSite(SITE_KEY)).thenReturn(site);
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));

        Long inquiryId = websiteInquiryService.siteNotifyInquiry(SITE_KEY, SECRET, createReqVO());

        assertEquals(7001L, inquiryId);
        verify(websiteInquiryMailService).ensureDeliveryAndSend(7001L);
    }

    private static WebsiteInquirySiteProperties.Site enabledSite(long tenantId, String secret) {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setTenantId(tenantId);
        site.setSharedSecret(secret);
        return site;
    }

    private static WebsiteInquirySiteProperties.Site disabledSite() {
        return new WebsiteInquirySiteProperties.Site();
    }

    private static AppWebsiteInquirySiteSubmitReqVO createReqVO() {
        AppWebsiteInquirySiteSubmitReqVO reqVO = new AppWebsiteInquirySiteSubmitReqVO();
        reqVO.setExternalInquiryId("2e82ae0d-5db2-4e94-bda0-0db994493885");
        reqVO.setName(" Alex   Morgan ");
        reqVO.setEmail("alex@example.com");
        reqVO.setCompanyName("Northstar Interiors");
        reqVO.setSubject("Hotel dining chair project");
        reqVO.setMessage("Please quote 120 chairs.\nLine 2");
        reqVO.setCategory("Dining Chairs");
        reqVO.setInquiryType("quote");
        reqVO.setSourcePage("/products/dining-room");
        reqVO.setLocale("en-GB");
        reqVO.setUtmSource("google");
        return reqVO;
    }

}
