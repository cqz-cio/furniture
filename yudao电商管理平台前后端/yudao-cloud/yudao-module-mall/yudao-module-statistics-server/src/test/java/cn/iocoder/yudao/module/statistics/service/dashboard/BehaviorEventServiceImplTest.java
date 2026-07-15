package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.BehaviorEventDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.BehaviorIngestionGapDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorEventMapper;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorIngestionGapMapper;
import cn.iocoder.yudao.module.statistics.enums.dashboard.BehaviorEventTypeEnum;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BehaviorEventServiceImplTest {
    @Test void stringRedisTemplateUsesExplicitBeanName() throws Exception {
        Resource resource = BehaviorEventServiceImpl.class.getDeclaredField("redisTemplate").getAnnotation(Resource.class);
        assertNotNull(resource);
        assertEquals("stringRedisTemplate", resource.name());
    }
    private BehaviorEventMapper eventMapper;
    private BehaviorIngestionGapService gapService;
    private ValueOperations<String,String> values;
    private BehaviorEventServiceImpl service;

    @BeforeEach void setUp() throws Exception {
        TenantContextHolder.setTenantId(121L);
        eventMapper=mock(BehaviorEventMapper.class); gapService=mock(BehaviorIngestionGapService.class);
        StringRedisTemplate redis=mock(StringRedisTemplate.class); values=mock(ValueOperations.class); when(redis.opsForValue()).thenReturn(values);
        BehaviorTrackingProperties props=new BehaviorTrackingProperties(); props.setEnabled(true); props.setConsentRequired(true); props.setEnabledTenantIds(Collections.singletonList(121L));
        service=new BehaviorEventServiceImpl();
        ReflectionTestUtils.setField(service,"properties",props);
        ReflectionTestUtils.setField(service,"hasher",new BehaviorIdentityHasher((t,v)->new byte[32]));
        ReflectionTestUtils.setField(service,"versionService",(BehaviorHmacDayVersionService)(t,d)->1);
        ReflectionTestUtils.setField(service,"eventMapper",eventMapper); ReflectionTestUtils.setField(service,"gapService",gapService); ReflectionTestUtils.setField(service,"redisTemplate",redis);
        ReflectionTestUtils.setField(service,"consentEvidenceVerifier",(ConsentEvidenceVerifier)(tenant,evidence,now)->"valid-evidence".equals(evidence));
        when(values.increment(anyString())).thenReturn(1L);
    }
    @AfterEach void clean(){TenantContextHolder.clear();}

    @Test void acceptedPublicEvent_isHashedAndInserted() {
        when(values.setIfAbsent(anyString(),eq("evt-1"),eq(5L),eq(TimeUnit.SECONDS))).thenReturn(true);
        service.trackPublic(request(BehaviorEventTypeEnum.HOME_VIEW.getValue()),"visitor","session","valid-evidence","203.0.113.9",null);
        org.mockito.ArgumentCaptor<BehaviorEventDO> captor=org.mockito.ArgumentCaptor.forClass(BehaviorEventDO.class); verify(eventMapper).insert(captor.capture());
        assertEquals(64,captor.getValue().getVisitorHash().length()); assertEquals(64,captor.getValue().getSessionHash().length());
        assertNotEquals("visitor",captor.getValue().getVisitorHash()); assertEquals(1,captor.getValue().getEventSource());
    }
    @Test void publicAddAndMissingConsent_areRejected() {
        assertThrows(IllegalArgumentException.class,()->service.trackPublic(request(3),"v","s","valid-evidence","203.0.113.9",null));
        assertThrows(IllegalArgumentException.class,()->service.trackPublic(request(1),"v","s",null,"203.0.113.9",null));
        verifyNoInteractions(eventMapper);
    }
    @Test void trustedCartEvent_requiresVerifiedConsentEvidence() {
        TrustedBehaviorEventCommand command = new TrustedBehaviorEventCommand()
                .setEventId("cart-1").setUserId(10L).setSpuId(20L).setSkuId(30L).setQuantity(1)
                .setRawVisitorId("visitor").setRawSessionId("session").setConsentEvidence("forged");
        assertThrows(IllegalArgumentException.class, () -> service.recordTrusted(command));
        command.setConsentEvidence(null);
        assertThrows(IllegalArgumentException.class, () -> service.recordTrusted(command));
        verifyNoInteractions(eventMapper);
    }
    @Test void redisFailure_failsClosedAndRecordsAnonymousGap() {
        when(values.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThrows(IllegalStateException.class,()->service.trackPublic(request(1),"secret-v","secret-s","valid-evidence","203.0.113.9",null));
        verify(eventMapper,never()).insert(any(BehaviorEventDO.class));
        verify(gapService).recordRejected(any(),eq("RATE_REDIS_UNAVAILABLE"));
    }
    @Test void exceededRateLimit_returns429SemanticAndDoesNotInsert() {
        when(values.increment(anyString())).thenReturn(30001L);
        assertThrows(BehaviorEventServiceImpl.BehaviorRateLimitException.class,
                ()->service.trackPublic(request(1),"v","s","valid-evidence","203.0.113.9",null));
        verify(eventMapper,never()).insert(any(BehaviorEventDO.class));
        verifyNoInteractions(gapService);
    }
    private AppBehaviorEventTrackReqVO request(int type){return new AppBehaviorEventTrackReqVO().setEventId("evt-1").setEventType(type).setPagePath("/?token=secret");}
}
