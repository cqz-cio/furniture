package cn.iocoder.yudao.module.seo.dal.redis.page;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import java.time.Duration;
import java.util.List;

@Repository
public class WebsitePagePreviewRedisDAO {
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]) end; return v", String.class);
    @Resource private StringRedisTemplate stringRedisTemplate;
    private String key(String kind, String token) { return "website-page:preview:" + kind + ":" + DigestUtil.sha256Hex(token); }
    public void setTicket(String token, WebsitePagePreviewGrant grant) {
        stringRedisTemplate.opsForValue().set(key("ticket", token), JsonUtils.toJsonString(grant), Duration.ofMinutes(2));
    }
    public WebsitePagePreviewGrant consumeTicket(String token) {
        String value = stringRedisTemplate.execute(CONSUME, List.of(key("ticket", token)));
        return value == null ? null : JsonUtils.parseObject(value, WebsitePagePreviewGrant.class);
    }
    public void setSession(String token, WebsitePagePreviewGrant grant) {
        stringRedisTemplate.opsForValue().set(key("session", token), JsonUtils.toJsonString(grant), Duration.ofMinutes(15));
    }
    public WebsitePagePreviewGrant getSession(String token) {
        String value = stringRedisTemplate.opsForValue().get(key("session", token));
        return value == null ? null : JsonUtils.parseObject(value, WebsitePagePreviewGrant.class);
    }
}
