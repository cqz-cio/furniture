package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import lombok.Value;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
public class FurnitureAssistantAiRequest {

    String message;
    String fallbackAnswer;
    List<FurnitureAssistantChatRespVO.Product> products;
    List<FurnitureAssistantKnowledgeMatch> knowledgeMatches;
    String conversationContext;
    FurnitureMatchType matchType;
    List<String> matchedConstraints;
    List<String> unmetConstraints;

    public FurnitureAssistantAiRequest(String message, String fallbackAnswer,
                                       List<FurnitureAssistantChatRespVO.Product> products,
                                       List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        this(message, fallbackAnswer, products, knowledgeMatches, "No earlier conversation is available.");
    }

    public FurnitureAssistantAiRequest(String message, String fallbackAnswer,
                                       List<FurnitureAssistantChatRespVO.Product> products,
                                       List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                                       String conversationContext) {
        this(message, fallbackAnswer, products, knowledgeMatches, conversationContext,
                products == null || products.isEmpty() ? FurnitureMatchType.NONE : FurnitureMatchType.EXACT,
                Collections.emptyList(), Collections.emptyList());
    }

    public FurnitureAssistantAiRequest(String message, String fallbackAnswer,
                                       List<FurnitureAssistantChatRespVO.Product> products,
                                       List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                                       String conversationContext, FurnitureMatchType matchType,
                                       List<String> matchedConstraints, List<String> unmetConstraints) {
        this.message = message;
        this.fallbackAnswer = fallbackAnswer;
        this.products = products;
        this.knowledgeMatches = knowledgeMatches;
        this.conversationContext = conversationContext;
        this.matchType = matchType == null ? FurnitureMatchType.NONE : matchType;
        this.matchedConstraints = immutableCopy(matchedConstraints);
        this.unmetConstraints = immutableCopy(unmetConstraints);
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

}
