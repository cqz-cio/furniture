package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/** Fail-closed tenant/user export limiter: three exports per rolling Redis window. */
@Component
public class DashboardExportRateLimiter {

    private static final long LIMIT = 3L;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('INCR', KEYS[1]); "
                    + "if value == 1 then redis.call('EXPIRE', KEYS[1], 600); end; return value;",
            Long.class);

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    public void acquire(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalStateException("Dashboard export requires an authenticated tenant user.");
        }
        try {
            Long count = redisTemplate.execute(SCRIPT,
                    Collections.singletonList("dashboard:export:" + tenantId + ":" + userId));
            if (count == null || count > LIMIT) {
                throw new IllegalStateException("Dashboard export limit exceeded: maximum 3 exports per 10 minutes.");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Dashboard export protection is unavailable; export was rejected.", exception);
        }
    }
}
