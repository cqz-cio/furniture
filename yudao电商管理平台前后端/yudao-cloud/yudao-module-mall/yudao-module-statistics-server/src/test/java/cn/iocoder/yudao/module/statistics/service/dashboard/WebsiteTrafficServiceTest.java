package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.AppWebsiteTrafficController;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.WebsiteTrafficEventMapper;
import cn.iocoder.yudao.module.statistics.framework.config.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class WebsiteTrafficServiceTest {
    WebsiteTrafficService service;
    WebsiteTrafficEventMapper events;
    BehaviorTrackingProperties behavior;
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(163L);
        service=new WebsiteTrafficService(); events=mock(WebsiteTrafficEventMapper.class);
        behavior=new BehaviorTrackingProperties(); behavior.setEnabled(true); behavior.setEnabledTenantIds(List.of(163L));
        var props=new WebsiteTrafficProperties(); props.setEnabledFrom(Map.of("163",today));
        ReflectionTestUtils.setField(service,"website",props); ReflectionTestUtils.setField(service,"behavior",behavior); ReflectionTestUtils.setField(service,"events",events);
        when(events.summary(eq(163L),any(),any())).thenReturn(new WebsiteTrafficRespVO().setHomePv(4L).setHomeUv(2L));
        when(events.gaps(anyLong(),any(),any())).thenReturn(List.of());
        when(events.trend(anyLong(),any(),any())).thenReturn(List.of(new WebsiteTrafficRespVO().setDay(today).setHomePv(4L).setHomeUv(2L)));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void distinguishesNotCollectedDaysFromMeasuredZeroAndUsesTenantInQuery() {
        var query=new DashboardQueryReqVO().setStartDate(today.minusDays(1)).setEndDate(today);
        var summary=service.summary(query);
        assertThat(summary.getTrafficDataStatus()).isEqualTo("PARTIAL"); assertThat(summary.getHomePv()).isEqualTo(4L);
        var rows=service.trend(query); assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getHomePv()).isNull(); assertThat(rows.get(0).getTrafficDataStatus()).isEqualTo("UNAVAILABLE");
        assertThat(rows.get(1).getHomeUv()).isEqualTo(2L);
        verify(events).summary(163L,today,today);
        behavior.setEnabled(false);
        assertThat(service.summary(query).getHomePv()).isNull(); assertThat(service.config()).containsEntry("enabled",false);
        TenantContextHolder.setTenantId(162L); assertThat(service.configured()).isFalse();
    }
    @Test void ingestionGapMarksCountPartialAndUnboundedQueriesAreRejected() {
        when(events.gaps(eq(163L),any(),any())).thenReturn(List.of(today));
        assertThat(service.summary(new DashboardQueryReqVO().setStartDate(today).setEndDate(today)).getTrafficDataStatus()).isEqualTo("PARTIAL");
        assertThatThrownBy(() -> service.summary(new DashboardQueryReqVO().setStartDate(today.minusDays(90)).setEndDate(today))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publicWebsiteCollectorRejectsPreviewAndNonPageEvents() {
        var controller=new AppWebsiteTrafficController(); var collector=mock(BehaviorEventService.class);
        ReflectionTestUtils.setField(controller,"website",service); ReflectionTestUtils.setField(controller,"behavior",collector);
        var request=new AppBehaviorEventTrackReqVO().setEventId("event").setEventType(5).setPagePath("/cms-preview");
        String id="12345678-1234-4123-8123-123456789abc";
        assertThatThrownBy(() -> controller.track(request,id,id,"consent")).isInstanceOf(IllegalArgumentException.class);
        request.setPagePath("/").setEventType(10);
        assertThatThrownBy(() -> controller.track(request,id,id,"consent")).isInstanceOf(IllegalArgumentException.class);
        request.setEventType(5).setPagePath("/contact?email=private");
        assertThatThrownBy(() -> controller.track(request,id,id,"consent")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(collector);
    }
}
