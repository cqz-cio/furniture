package cn.iocoder.yudao.module.product.service.furniture.conversation;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class FurnitureAssistantConversation {

    public static final int MAX_MESSAGES = 12;

    private String conversationId;
    private Long userId;
    private List<Message> messages = new ArrayList<>();
    private FurnitureAssistantRequirements requirements = new FurnitureAssistantRequirements();
    private List<RecommendationRef> lastRecommendations = new ArrayList<>();
    private List<Long> likedProductIds = new ArrayList<>();
    private List<Long> excludedProductIds = new ArrayList<>();
    private long updatedAt;

    public static FurnitureAssistantConversation newConversation(String conversationId) {
        FurnitureAssistantConversation conversation = new FurnitureAssistantConversation();
        conversation.setConversationId(conversationId);
        conversation.setUpdatedAt(System.currentTimeMillis());
        return conversation;
    }

    public void appendMessage(String role, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        messages.add(new Message(role, content, System.currentTimeMillis()));
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
        updatedAt = System.currentTimeMillis();
    }

    @Data
    public static class Message {
        private String role;
        private String content;
        private long createdAt;

        public Message() {
        }

        public Message(String role, String content, long createdAt) {
            this.role = role;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    @Data
    public static class RecommendationRef {
        private Long productId;
        private Long skuId;
        private BigDecimal price;

        public RecommendationRef() {
        }

        public RecommendationRef(Long productId, Long skuId, BigDecimal price) {
            this.productId = productId;
            this.skuId = skuId;
            this.price = price;
        }
    }

}
