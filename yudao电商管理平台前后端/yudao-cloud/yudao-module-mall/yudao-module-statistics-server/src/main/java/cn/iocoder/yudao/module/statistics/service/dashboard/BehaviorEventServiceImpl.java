package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.BehaviorEventDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorEventMapper;
import cn.iocoder.yudao.module.statistics.enums.dashboard.*;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.security.GeneralSecurityException;
import java.time.*;
import java.util.concurrent.TimeUnit;
import cn.hutool.crypto.SecureUtil;

@Service @Slf4j
public class BehaviorEventServiceImpl implements BehaviorEventService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private BehaviorTrackingProperties properties;
    @Resource private BehaviorIdentityHasher hasher;
    @Resource private BehaviorHmacDayVersionService versionService;
    @Resource private BehaviorEventMapper eventMapper;
    @Resource private BehaviorIngestionGapService gapService;
    @Resource(name = "stringRedisTemplate") private StringRedisTemplate redisTemplate;

    @Override @Transactional(rollbackFor=Exception.class)
    public void trackPublic(AppBehaviorEventTrackReqVO request, String rawVisitorId, String rawSessionId, String clientIp, Long userId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!properties.isEnabled() || !properties.getEnabledTenantIds().contains(tenantId)) throw new IllegalStateException("tracking is disabled");
        if (properties.isConsentRequired() && !Boolean.TRUE.equals(request.getConsentGranted())) throw new IllegalArgumentException("analytics consent is required");
        BehaviorEventTypeEnum type = BehaviorEventTypeEnum.of(request.getEventType());
        if (type == BehaviorEventTypeEnum.ADD_TO_CART) throw new IllegalArgumentException("public add-to-cart is forbidden");
        LocalDateTime receivedAt = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate day = receivedAt.toLocalDate();
        int version = versionService.activeVersion(tenantId, day);
        try {
            String visitorHash = hasher.hash(tenantId, version, rawVisitorId);
            String sessionHash = hasher.hash(tenantId, version, rawSessionId);
            try { enforceRateLimits(tenantId, visitorHash, clientIp); }
            catch (RuntimeException redisFailure) {
                if (redisFailure instanceof BehaviorRateLimitException) throw redisFailure;
                gapService.recordRejected(receivedAt, "RATE_REDIS_UNAVAILABLE");
                throw new IllegalStateException("tracking temporarily unavailable");
            }
            String duplicateKey = "statistics:behavior:dedupe:" + tenantId + ':' + visitorHash + ':' + type.getValue() + ':' + request.getSpuId();
            Boolean accepted;
            try { accepted = redisTemplate.opsForValue().setIfAbsent(duplicateKey, request.getEventId(), 5, TimeUnit.SECONDS); }
            catch (RuntimeException redisFailure) { gapService.recordRejected(receivedAt, "DEDUP_REDIS_UNAVAILABLE"); log.error("[behaviorIngestionGap][reason(DEDUP_REDIS_UNAVAILABLE) day({})]", day); throw new IllegalStateException("tracking temporarily unavailable"); }
            if (!Boolean.TRUE.equals(accepted)) return;
            BehaviorEventDO event = new BehaviorEventDO().setEventId(request.getEventId()).setEventType(type.getValue())
                    .setEventSource(BehaviorEventSourceEnum.PUBLIC_WEB.value).setVisitorHash(visitorHash).setSessionHash(sessionHash)
                    .setHashKeyVersion(version).setUserId(userId).setSpuId(request.getSpuId()).setSkuId(request.getSkuId())
                    .setPagePath(normalizePath(request.getPagePath())).setReferrerHost(request.getReferrerHost())
                    .setDeviceType(request.getDeviceType() == null ? 9 : request.getDeviceType())
                    .setTrafficQuality(TrafficQualityEnum.ACCEPTED.value).setOccurredAt(receivedAt).setEventDay(day);
            insertIdempotently(event);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("identity hashing failed", ex); }
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public void recordTrusted(TrustedBehaviorEventCommand command) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime receivedAt = LocalDateTime.now(BUSINESS_ZONE); LocalDate day = receivedAt.toLocalDate();
        int version = versionService.activeVersion(tenantId, day);
        try {
            String visitorRaw = command.getRawVisitorId() != null ? command.getRawVisitorId() : "user:" + command.getUserId();
            BehaviorEventDO event = new BehaviorEventDO().setEventId(command.getEventId())
                    .setEventType(BehaviorEventTypeEnum.ADD_TO_CART.getValue()).setEventSource(BehaviorEventSourceEnum.SERVER_CART.value)
                    .setVisitorHash(hasher.hash(tenantId, version, visitorRaw))
                    .setSessionHash(command.getRawSessionId() == null ? null : hasher.hash(tenantId, version, command.getRawSessionId()))
                    .setHashKeyVersion(version).setUserId(command.getUserId()).setSpuId(command.getSpuId()).setSkuId(command.getSkuId())
                    .setQuantity(command.getQuantity()).setPagePath("/cart").setDeviceType(9)
                    .setTrafficQuality(TrafficQualityEnum.ACCEPTED.value).setOccurredAt(receivedAt).setEventDay(day);
            insertIdempotently(event);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("identity hashing failed", ex); }
    }

    private void insertIdempotently(BehaviorEventDO event) {
        try { eventMapper.insert(event); }
        catch (DuplicateKeyException ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("uk_tenant_event")) throw ex;
        }
    }
    private String normalizePath(String path) {
        String clean = path == null ? "/" : path.split("[?#]",2)[0];
        if (!clean.startsWith("/") || clean.contains("..")) throw new IllegalArgumentException("invalid page path");
        return clean;
    }
    private void enforceRateLimits(Long tenantId,String visitorHash,String clientIp) {
        long minute=System.currentTimeMillis()/60000L;
        incrementOrReject("statistics:behavior:rate:global:"+minute,properties.getGlobalPerMinute());
        incrementOrReject("statistics:behavior:rate:tenant:"+tenantId+':'+minute,properties.getPerTenantPerMinute());
        incrementOrReject("statistics:behavior:rate:visitor:"+tenantId+':'+visitorHash+':'+minute,properties.getPerVisitorPerMinute());
        incrementOrReject("statistics:behavior:rate:ip:"+tenantId+':'+SecureUtil.sha256(tenantId+":"+clientIp)+':'+minute,properties.getPerIpPerMinute());
    }
    private void incrementOrReject(String key,int limit) {
        Long value=redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) redisTemplate.expire(key,2,TimeUnit.MINUTES);
        if (value == null || value > limit) throw new BehaviorRateLimitException();
    }
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
    public static class BehaviorRateLimitException extends RuntimeException {}
}
