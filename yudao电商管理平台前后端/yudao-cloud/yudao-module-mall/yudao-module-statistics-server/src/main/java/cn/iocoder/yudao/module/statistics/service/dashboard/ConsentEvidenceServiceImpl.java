package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceIssueReqVO;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceRespVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.ConsentEvidenceDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.ConsentEvidenceMapper;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class ConsentEvidenceServiceImpl implements ConsentEvidenceService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private BehaviorTrackingProperties properties;
    @Resource
    private HmacConsentEvidenceCodec codec;
    @Resource
    private ConsentEvidenceMapper mapper;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppConsentEvidenceRespVO issue(AppConsentEvidenceIssueReqVO request, String clientIp) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireEnabledTenant(tenantId);
        if (!Boolean.TRUE.equals(request.getAnalytics())) {
            throw new IllegalArgumentException("analytics consent is required before evidence can be issued");
        }
        if (!Objects.equals(properties.getConsentPolicyVersion(), request.getPolicyVersion())) {
            throw new IllegalArgumentException("unsupported consent policy version");
        }
        enforceRateLimit(tenantId, clientIp);

        long nowEpoch = Instant.now().getEpochSecond();
        ConsentEvidenceDO existing = mapper.selectByConsentId(tenantId, request.getConsentId());
        if (existing != null) return restoreExisting(tenantId, request, existing, nowEpoch);

        long expiresEpoch = Instant.ofEpochSecond(nowEpoch)
                .plus(properties.getConsentEvidenceLifetimeDays(), ChronoUnit.DAYS)
                .getEpochSecond();
        ConsentEvidenceDO record = new ConsentEvidenceDO()
                .setConsentId(request.getConsentId())
                .setPolicyVersion(request.getPolicyVersion())
                .setEvidenceNonce(randomNonce())
                .setPreferences(request.getPreferences())
                .setAnalytics(true)
                .setMarketing(request.getMarketing())
                .setIssuedEpoch(nowEpoch)
                .setExpiresEpoch(expiresEpoch);
        record.setTenantId(tenantId);
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException race) {
            ConsentEvidenceDO concurrent = mapper.selectByConsentId(tenantId, request.getConsentId());
            if (concurrent == null) throw race;
            return restoreExisting(tenantId, request, concurrent, nowEpoch);
        }
        return response(tenantId, record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String evidence) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireEnabledTenant(tenantId);
        Instant now = Instant.now();
        ConsentEvidenceClaims claims = codec.verifyAndDecode(tenantId, evidence, now);
        if (claims == null) throw new IllegalArgumentException("invalid analytics consent evidence");
        mapper.withdraw(tenantId, claims.nonce(), now.getEpochSecond());
    }

    private AppConsentEvidenceRespVO restoreExisting(Long tenantId,
                                                      AppConsentEvidenceIssueReqVO request,
                                                      ConsentEvidenceDO existing,
                                                      long nowEpoch) {
        boolean sameDecision = existing.getWithdrawnEpoch() == null
                && existing.getExpiresEpoch() != null
                && existing.getExpiresEpoch() >= nowEpoch
                && Objects.equals(existing.getPolicyVersion(), request.getPolicyVersion())
                && Objects.equals(existing.getPreferences(), request.getPreferences())
                && Boolean.TRUE.equals(existing.getAnalytics())
                && Objects.equals(existing.getMarketing(), request.getMarketing());
        if (!sameDecision) throw new IllegalArgumentException("consentId is already used for another decision");
        return response(tenantId, existing);
    }

    private AppConsentEvidenceRespVO response(Long tenantId, ConsentEvidenceDO record) {
        String evidence = codec.issue(
                tenantId,
                record.getEvidenceNonce(),
                Instant.ofEpochSecond(record.getIssuedEpoch()),
                Instant.ofEpochSecond(record.getExpiresEpoch()));
        return new AppConsentEvidenceRespVO(
                evidence, record.getExpiresEpoch(), record.getPolicyVersion());
    }

    private void requireEnabledTenant(Long tenantId) {
        if (!properties.isEnabled() || !properties.getEnabledTenantIds().contains(tenantId)) {
            throw new IllegalStateException("tracking is disabled");
        }
    }

    private void enforceRateLimit(Long tenantId, String clientIp) {
        long minute = System.currentTimeMillis() / 60_000L;
        String ipHash = SecureUtil.sha256(tenantId + ":" + String.valueOf(clientIp));
        String key = "statistics:consent:rate:" + tenantId + ':' + ipHash + ':' + minute;
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) redisTemplate.expire(key, 2, TimeUnit.MINUTES);
            if (value == null || value > properties.getConsentPerIpPerMinute()) {
                throw new ConsentRateLimitException();
            }
        } catch (ConsentRateLimitException exception) {
            throw exception;
        } catch (RuntimeException redisFailure) {
            throw new IllegalStateException("consent service temporarily unavailable", redisFailure);
        }
    }

    private String randomNonce() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class ConsentRateLimitException extends RuntimeException {
    }
}
