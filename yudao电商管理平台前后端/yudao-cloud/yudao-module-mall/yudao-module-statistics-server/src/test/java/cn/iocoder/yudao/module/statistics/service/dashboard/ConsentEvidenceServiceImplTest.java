package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceIssueReqVO;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceRespVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.ConsentEvidenceDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.ConsentEvidenceMapper;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsentEvidenceServiceImplTest {

    private ConsentEvidenceMapper mapper;
    private ValueOperations<String, String> values;
    private HmacConsentEvidenceCodec codec;
    private ConsentEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
        BehaviorTrackingProperties properties = new BehaviorTrackingProperties();
        properties.setEnabled(true);
        properties.setEnabledTenantIds(List.of(162L));
        properties.setConsentPolicyVersion("2026-08-06");
        properties.setConsentEvidenceLifetimeDays(180);
        properties.setConsentPerIpPerMinute(30);

        byte[] key = "tenant-162-consent-test-key-32-bytes".getBytes(StandardCharsets.UTF_8);
        codec = new HmacConsentEvidenceCodec(tenantId -> key, 180 * 86400L);
        mapper = mock(ConsentEvidenceMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);

        service = new ConsentEvidenceServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "codec", codec);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
    }

    @AfterEach
    void cleanUp() {
        TenantContextHolder.clear();
    }

    @Test
    void issuesAnonymousEvidenceAndCanWithdrawIt() {
        AppConsentEvidenceIssueReqVO request = acceptedRequest();
        when(mapper.selectByConsentId(162L, request.getConsentId())).thenReturn(null);

        AppConsentEvidenceRespVO response = service.issue(request, "203.0.113.9");

        var captor = org.mockito.ArgumentCaptor.forClass(ConsentEvidenceDO.class);
        verify(mapper).insert(captor.capture());
        ConsentEvidenceDO stored = captor.getValue();
        assertEquals(162L, stored.getTenantId());
        assertEquals(request.getConsentId(), stored.getConsentId());
        assertEquals("2026-08-06", stored.getPolicyVersion());
        assertTrue(stored.getAnalytics());
        assertFalse(stored.getMarketing());
        assertNull(stored.getWithdrawnEpoch());
        assertTrue(stored.getExpiresEpoch() > stored.getIssuedEpoch());
        assertEquals("2026-08-06", response.getPolicyVersion());
        assertEquals(stored.getExpiresEpoch(), response.getExpiresAtEpochSeconds());
        assertNotNull(codec.verifyAndDecode(162L, response.getEvidence(), Instant.now()));

        service.withdraw(response.getEvidence());
        verify(mapper).withdraw(eq(162L), eq(stored.getEvidenceNonce()), anyLong());
    }

    @Test
    void rejectsEvidenceIssuanceWithoutAnalyticsConsent() {
        AppConsentEvidenceIssueReqVO request = acceptedRequest().setAnalytics(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(request, "203.0.113.9"));
        verifyNoInteractions(mapper);
        verifyNoInteractions(values);
    }

    private AppConsentEvidenceIssueReqVO acceptedRequest() {
        return new AppConsentEvidenceIssueReqVO()
                .setConsentId("33333333-3333-4333-8333-333333333333")
                .setPolicyVersion("2026-08-06")
                .setPreferences(false)
                .setAnalytics(true)
                .setMarketing(false);
    }
}
