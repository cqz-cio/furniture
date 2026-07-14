package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardExportRateLimiterTest {

    @Test
    void permitsTheFirstThreeExportsAndRejectsTheFourthForTheSameTenantUser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList())).thenReturn(1L, 2L, 3L, 4L);
        DashboardExportRateLimiter limiter = limiter(redis);

        assertDoesNotThrow(() -> limiter.acquire(121L, 110L));
        assertDoesNotThrow(() -> limiter.acquire(121L, 110L));
        assertDoesNotThrow(() -> limiter.acquire(121L, 110L));
        assertThrows(IllegalStateException.class, () -> limiter.acquire(121L, 110L));
    }

    @Test
    void failsClosedWhenRedisCannotProtectTheExportBoundary() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList())).thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(IllegalStateException.class, () -> limiter(redis).acquire(121L, 110L));
    }

    private DashboardExportRateLimiter limiter(StringRedisTemplate redis) {
        DashboardExportRateLimiter limiter = new DashboardExportRateLimiter();
        ReflectionTestUtils.setField(limiter, "redisTemplate", redis);
        return limiter;
    }
}
