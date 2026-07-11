package cn.iocoder.yudao.module.product.service.furniture.conversation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.service.furniture.FurnitureAssistantProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@Slf4j
public class RedisFurnitureAssistantConversationStore implements FurnitureAssistantConversationStore {

    private static final String KEY_PREFIX = "furniture:assistant:conversation:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final FurnitureAssistantProperties properties;

    public RedisFurnitureAssistantConversationStore(RedisTemplate<String, Object> redisTemplate,
                                                     FurnitureAssistantProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<FurnitureAssistantConversation> find(String conversationId) {
        if (!properties.isMemoryEnabled() || StrUtil.isBlank(conversationId)) {
            return Optional.empty();
        }
        String key = key(conversationId);
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (!(value instanceof FurnitureAssistantConversation)) {
                return Optional.empty();
            }
            redisTemplate.expire(key, ttl());
            return Optional.of((FurnitureAssistantConversation) value);
        } catch (RuntimeException ex) {
            log.warn("Furniture assistant conversation read failed for key type {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void save(FurnitureAssistantConversation conversation) {
        if (!properties.isMemoryEnabled() || conversation == null
                || StrUtil.isBlank(conversation.getConversationId())) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(conversation.getConversationId()), conversation, ttl());
        } catch (RuntimeException ex) {
            log.warn("Furniture assistant conversation write failed for key type {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    public void delete(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            return;
        }
        try {
            redisTemplate.delete(key(conversationId));
        } catch (RuntimeException ex) {
            log.warn("Furniture assistant conversation delete failed for key type {}", ex.getClass().getSimpleName());
        }
    }

    private Duration ttl() {
        return Duration.ofHours(Math.max(1, properties.getMemoryTtlHours()));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

}
