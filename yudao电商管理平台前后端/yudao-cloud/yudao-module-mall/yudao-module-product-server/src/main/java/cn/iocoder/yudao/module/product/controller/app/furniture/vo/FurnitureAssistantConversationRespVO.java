package cn.iocoder.yudao.module.product.controller.app.furniture.vo;

import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantConversation;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;
import lombok.Data;

import java.util.List;

@Data
public class FurnitureAssistantConversationRespVO {
    private String conversationId;
    private List<FurnitureAssistantConversation.Message> messages;
    private FurnitureAssistantRequirements requirements;
    private List<FurnitureAssistantConversation.RecommendationRef> lastRecommendations;
    private List<FurnitureAssistantChatRespVO.Product> products;
}
