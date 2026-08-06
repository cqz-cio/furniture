package cn.iocoder.yudao.module.seo.dal.redis.blog;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;

@Repository
public class WebsiteBlogPreviewRedisDAO {

    private static final String TICKET_KEY_PREFIX = "website-blog:preview:ticket:";
    private static final String SESSION_KEY_PREFIX = "website-blog:preview:session:";
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void setTicket(String token, WebsiteBlogPreviewGrant grant, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key(TICKET_KEY_PREFIX, token), JsonUtils.toJsonString(grant), ttl);
    }

    public WebsiteBlogPreviewGrant consumeTicket(String token) {
        String json = stringRedisTemplate.execute(GET_AND_DELETE_SCRIPT,
                Collections.singletonList(key(TICKET_KEY_PREFIX, token)));
        return json == null ? null : JsonUtils.parseObject(json, WebsiteBlogPreviewGrant.class);
    }

    public void setSession(String token, WebsiteBlogPreviewGrant grant, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key(SESSION_KEY_PREFIX, token), JsonUtils.toJsonString(grant), ttl);
    }

    public WebsiteBlogPreviewGrant getSession(String token) {
        String json = stringRedisTemplate.opsForValue().get(key(SESSION_KEY_PREFIX, token));
        return json == null ? null : JsonUtils.parseObject(json, WebsiteBlogPreviewGrant.class);
    }

    private static String key(String prefix, String token) {
        return prefix + DigestUtil.sha256Hex(token);
    }

}
