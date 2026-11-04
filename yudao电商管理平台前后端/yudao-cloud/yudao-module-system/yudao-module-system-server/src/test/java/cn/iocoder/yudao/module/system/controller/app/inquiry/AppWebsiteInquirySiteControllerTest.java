package cn.iocoder.yudao.module.system.controller.app.inquiry;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.crm.api.inquiry.CrmWebsiteInquiryApi;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryCreateRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquiryProperties;
import cn.iocoder.yudao.module.system.framework.inquiry.config.WebsiteInquirySiteProperties;
import cn.iocoder.yudao.module.system.service.inquiry.WebsiteInquiryServiceImpl;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站点隔离端点 {@code /system/website-inquiry/site-notify} 的 HTTP 绑定与协议测试。
 *
 * <p>通过真实 {@link TenantContextWebFilter} + 真实 Service（依赖 mock）走完整
 * 请求头 → 租户上下文 → 站点解析 → 入库 → 响应链路，而非只做直接方法断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class AppWebsiteInquirySiteControllerTest {

    private static final long TENANT_ID = 900001L;
    private static final long CONTACT_USER_ID = 216L;
    private static final String SITE_KEY = "trendzgreen";
    private static final String SECRET = "a-random-server-only-secret-32-chars";

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

    @InjectMocks
    private WebsiteInquiryServiceImpl websiteInquiryService;

    private AppWebsiteInquirySiteController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AppWebsiteInquirySiteController();
        ReflectionTestUtils.setField(controller, "websiteInquiryService", websiteInquiryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class)))
                .addFilters(new TenantContextWebFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void siteNotify_success_returnsPersistedInquiryId() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));

        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7001));
        verify(crmWebsiteInquiryApi).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_catalog_preservesTypeAndCategory() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7002L, true));
        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"quote\"", "\"catalog\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7002));
        verify(crmWebsiteInquiryApi).createWebsiteInquiry(org.mockito.ArgumentMatchers.argThat(
                request -> "catalog".equals(request.getInquiryType())
                        && "Dining Chairs".equals(request.getCategory())));
    }

    @Test
    void siteNotify_unknownSite_returnsNonZeroCode() throws Exception {
        when(siteProperties.getSite("unknown")).thenReturn(null);

        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", "unknown")
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_002_029_001));
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_wrongSecret_returnsNonZeroCodeAndDoesNotLeakSecret() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        String response = mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", "a-different-wrong-secret")
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_002_029_001))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(response.contains(SECRET));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("a-different-wrong-secret"));
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_forgedTenantHeader_returnsNonZeroCode() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_002_029_001));
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_missingTenantHeader_returnsNonZeroCode() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));

        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_002_029_001));
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_bodyTenantAttempt_isIgnored() throws Exception {
        when(siteProperties.getSite(SITE_KEY)).thenReturn(enabledSite(TENANT_ID, SECRET));
        when(tenantService.getTenant(TENANT_ID)).thenReturn(
                new TenantDO().setId(TENANT_ID).setContactUserId(CONTACT_USER_ID));
        when(crmWebsiteInquiryApi.createWebsiteInquiry(any()))
                .thenReturn(new CrmWebsiteInquiryCreateRespDTO(7001L, true));

        // 请求体夹带 tenantId/site/secret 应被忽略：站点与租户只来自请求头 + 服务端配置。
        String smugglingBody = validBody()
                .replaceFirst("\\{", "{\"tenantId\":999,\"site\":\"evil\",\"secret\":\"x\",");
        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smugglingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(7001));
        verify(crmWebsiteInquiryApi).createWebsiteInquiry(any());
    }

    @Test
    void siteNotify_invalidBody_returns400BeforeService() throws Exception {
        String invalidBody = validBody().replace("\"inquiryType\":\"quote\",", "");

        mockMvc.perform(post("/system/website-inquiry/site-notify")
                        .header("X-Site-Inquiry-Site", SITE_KEY)
                        .header("X-Site-Inquiry-Secret", SECRET)
                        .header("tenant-id", String.valueOf(TENANT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(crmWebsiteInquiryApi, never()).createWebsiteInquiry(any());
    }

    private static WebsiteInquirySiteProperties.Site enabledSite(long tenantId, String secret) {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setTenantId(tenantId);
        site.setSharedSecret(secret);
        return site;
    }

    private static String validBody() {
        return "{\"externalInquiryId\":\"2e82ae0d-5db2-4e94-bda0-0db994493885\","
                + "\"name\":\"Alex Morgan\","
                + "\"email\":\"alex@example.com\","
                + "\"companyName\":\"Northstar Interiors\","
                + "\"subject\":\"Hotel dining chair project\","
                + "\"message\":\"Please quote 120 chairs.\","
                + "\"category\":\"Dining Chairs\","
                + "\"inquiryType\":\"quote\","
                + "\"sourcePage\":\"/products/dining-room\","
                + "\"locale\":\"en-GB\","
                + "\"utmSource\":\"google\"}";
    }

}
