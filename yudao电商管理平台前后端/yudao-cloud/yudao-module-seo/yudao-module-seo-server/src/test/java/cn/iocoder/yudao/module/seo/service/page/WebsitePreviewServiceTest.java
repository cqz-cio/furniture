package cn.iocoder.yudao.module.seo.service.page;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.redis.page.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class WebsitePreviewServiceTest {
    WebsitePreviewService service;
    WebsitePreviewRedisDAO tokens;
    SeoSiteConfigMapper sites;
    Map<String, WebsitePreviewGrant> tickets, sessions;
    String token = "spv_" + "a".repeat(43);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(163L);
        service = new WebsitePreviewService(); tokens = mock(WebsitePreviewRedisDAO.class); sites = mock(SeoSiteConfigMapper.class);
        ReflectionTestUtils.setField(service,"tokens",tokens); ReflectionTestUtils.setField(service,"sites",sites);
        var site = new SeoSiteConfigDO().setSiteId(1L).setNavigationTemplate("TRIPEER_CORPORATE").setPreviewBaseUrl("https://site.example"); site.setTenantId(163L);
        when(sites.selectBySiteId(1L)).thenReturn(site);
        tickets = new HashMap<>(); sessions = new HashMap<>();
        tickets.put(token, new WebsitePreviewGrant(163L,1L,"https://site.example","en",null,null,null));
        when(tokens.consumeTicket(anyString())).thenAnswer(c -> tickets.remove(c.getArgument(0)));
        doAnswer(c -> { sessions.put(c.getArgument(0),c.getArgument(1)); return null; }).when(tokens).setSession(anyString(),any());
        when(tokens.getSession(anyString())).thenAnswer(c -> sessions.get(c.getArgument(0)));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void oneTimeTicketAndSessionAreBoundToTenantOriginAndCurrentConfig() {
        var exchanged=service.exchange(token,"https://site.example");
        String session=(String)exchanged.get("previewSession");
        assertThat(service.get(session,"https://site.example")).containsEntry("locale","en").doesNotContainKeys("tenantId","origin");
        assertThatThrownBy(() -> service.exchange(token,"https://site.example")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.get(session,"https://other.example")).isInstanceOf(RuntimeException.class);
        TenantContextHolder.setTenantId(162L);
        assertThatThrownBy(() -> service.get(session,"https://site.example")).isInstanceOf(RuntimeException.class);
        TenantContextHolder.setTenantId(163L); sessions.clear();
        assertThatThrownBy(() -> service.get(session,"https://site.example")).isInstanceOf(RuntimeException.class);
    }
    @Test void invalidTicketCannotReachStorage() {
        assertThatThrownBy(() -> service.exchange("invalid", "https://site.example")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(tokens);
    }
}
