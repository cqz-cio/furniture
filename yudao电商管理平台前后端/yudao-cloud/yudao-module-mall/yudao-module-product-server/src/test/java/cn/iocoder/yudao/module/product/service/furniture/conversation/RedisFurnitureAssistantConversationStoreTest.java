package cn.iocoder.yudao.module.product.service.furniture.conversation;

import cn.iocoder.yudao.module.product.service.furniture.FurnitureAssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFurnitureAssistantConversationStoreTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private RedisFurnitureAssistantConversationStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        FurnitureAssistantProperties properties = new FurnitureAssistantProperties();
        properties.setMemoryTtlHours(24);
        store = new RedisFurnitureAssistantConversationStore(redisTemplate, properties);
    }

    @Test
    void saveShouldUseNamespacedKeyAndTwentyFourHourTtl() {
        store.save(FurnitureAssistantConversation.newConversation("c-1"));

        verify(valueOperations).set(eq("furniture:assistant:conversation:c-1"), any(), eq(Duration.ofHours(24)));
    }

    @Test
    void findShouldRenewTtl() {
        String key = "furniture:assistant:conversation:c-1";
        when(valueOperations.get(key)).thenReturn(FurnitureAssistantConversation.newConversation("c-1"));

        store.find("c-1");

        verify(redisTemplate).expire(key, Duration.ofHours(24));
    }

}
