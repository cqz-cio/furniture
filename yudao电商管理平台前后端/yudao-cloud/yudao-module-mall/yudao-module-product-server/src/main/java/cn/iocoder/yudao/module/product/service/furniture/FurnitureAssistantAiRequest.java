package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import lombok.Value;

import java.util.List;

@Value
public class FurnitureAssistantAiRequest {

    String message;
    String fallbackAnswer;
    List<FurnitureAssistantChatRespVO.Product> products;
    List<FurnitureAssistantKnowledgeMatch> knowledgeMatches;
    String conversationContext;

    public FurnitureAssistantAiRequest(String message, String fallbackAnswer,
                                       List<FurnitureAssistantChatRespVO.Product> products,
                                       List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        this(message, fallbackAnswer, products, knowledgeMatches, "No earlier conversation is available.");
    }

    public FurnitureAssistantAiRequest(String message, String fallbackAnswer,
                                       List<FurnitureAssistantChatRespVO.Product> products,
                                       List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                                       String conversationContext) {
        this.message = message;
        this.fallbackAnswer = fallbackAnswer;
        this.products = products;
        this.knowledgeMatches = knowledgeMatches;
        this.conversationContext = conversationContext;
    }

}
