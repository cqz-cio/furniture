package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantConversationRespVO;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantConversation;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantConversationStore;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirementMerger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Resource;

@Service
@Slf4j
public class FurnitureAssistantServiceImpl implements FurnitureAssistantService {

    private final FurnitureProductSearchTool productSearchTool;
    private final FurnitureAssistantKnowledgeService knowledgeService;
    private final FurnitureAssistantAiClient aiClient;

    @Resource
    private FurnitureAssistantConversationStore conversationStore;

    @Resource
    private FurnitureAssistantRequirementMerger requirementMerger;

    public FurnitureAssistantServiceImpl(FurnitureProductSearchTool productSearchTool,
                                         FurnitureAssistantKnowledgeService knowledgeService,
                                         FurnitureAssistantAiClient aiClient) {
        this.productSearchTool = productSearchTool;
        this.knowledgeService = knowledgeService;
        this.aiClient = aiClient;
    }

    @Override
    public FurnitureAssistantChatRespVO chat(FurnitureAssistantChatReqVO reqVO) {
        String message = reqVO.getMessage().trim();
        String conversationId = StrUtil.blankToDefault(reqVO.getConversationId(), UUID.randomUUID().toString());
        FurnitureAssistantConversation conversation = loadConversation(conversationId);
        conversation.appendMessage("user", message);
        if (requirementMerger != null) {
            requirementMerger.merge(conversation, message);
        }
        List<FurnitureAssistantKnowledgeMatch> knowledgeMatches = knowledgeService.search(message);
        boolean shouldSearchProducts = productSearchTool.shouldSearchProducts(message, knowledgeMatches);
        String searchMessage = buildSearchMessage(message, conversation);
        FurnitureProductSearchResult searchResult = shouldSearchProducts
                ? productSearchTool.searchForAssistant(searchMessage) : FurnitureProductSearchResult.empty();

        FurnitureAssistantChatRespVO respVO = new FurnitureAssistantChatRespVO();
        List<FurnitureAssistantChatRespVO.Product> products = searchResult.getProducts();
        String fallbackAnswer = buildAnswer(message, products.size(), knowledgeMatches);
        AiAnswer aiAnswer = buildAiBackedAnswer(message, fallbackAnswer, products, knowledgeMatches);
        respVO.setAnswer(aiAnswer.getAnswer());
        products = products.stream().filter(product -> !conversation.getExcludedProductIds().contains(product.getId()))
                .collect(Collectors.toList());
        respVO.setProducts(products);
        respVO.setSources(buildSources(shouldSearchProducts, knowledgeMatches, aiAnswer.isModelBacked()));
        respVO.setConversationId(conversationId);
        respVO.setRequirements(conversation.getRequirements());
        respVO.setMissingFields(Collections.emptyList());
        conversation.appendMessage("assistant", respVO.getAnswer());
        conversation.setLastRecommendations(products.stream()
                .map(product -> new FurnitureAssistantConversation.RecommendationRef(
                        product.getId(), product.getSkuId(), product.getPrice()))
                .collect(Collectors.toList()));
        if (conversationStore != null) {
            conversationStore.save(conversation);
        }
        return respVO;
    }

    @Override
    public FurnitureAssistantConversationRespVO getConversation(String conversationId) {
        FurnitureAssistantConversation value = conversationStore == null ? null
                : conversationStore.find(conversationId).orElse(null);
        if (value == null) return null;
        FurnitureAssistantConversationRespVO response = new FurnitureAssistantConversationRespVO();
        response.setConversationId(value.getConversationId());
        response.setMessages(value.getMessages());
        response.setRequirements(value.getRequirements());
        response.setLastRecommendations(value.getLastRecommendations());
        return response;
    }

    @Override
    public void deleteConversation(String conversationId) {
        if (conversationStore != null) conversationStore.delete(conversationId);
    }

    private FurnitureAssistantConversation loadConversation(String conversationId) {
        Optional<FurnitureAssistantConversation> stored = conversationStore == null
                ? Optional.empty() : conversationStore.find(conversationId);
        return stored.orElseGet(() -> FurnitureAssistantConversation.newConversation(conversationId));
    }

    private String buildSearchMessage(String message, FurnitureAssistantConversation conversation) {
        StringBuilder value = new StringBuilder(message);
        if (conversation.getRequirements().getCategory() != null) value.append(' ').append(conversation.getRequirements().getCategory());
        if (conversation.getRequirements().getBudgetMax() != null) value.append(" under ").append(conversation.getRequirements().getBudgetMax());
        return value.toString();
    }

    private AiAnswer buildAiBackedAnswer(String message, String fallbackAnswer,
                                         List<FurnitureAssistantChatRespVO.Product> products,
                                         List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        if (!aiClient.isEnabled()) {
            return AiAnswer.fallback(fallbackAnswer);
        }
        try {
            String aiAnswer = aiClient.generateAnswer(new FurnitureAssistantAiRequest(
                    message, fallbackAnswer, products, knowledgeMatches));
            if (StrUtil.isBlank(aiAnswer)) {
                return AiAnswer.fallback(fallbackAnswer);
            }
            return AiAnswer.model(prepareDisplayAnswer(aiAnswer, message));
        } catch (Exception ex) {
            log.warn("[buildAiBackedAnswer][AI answer generation failed, fallback to deterministic answer]", ex);
            return AiAnswer.fallback(fallbackAnswer);
        }
    }

    private String prepareDisplayAnswer(String answer, String message) {
        String cleaned = StrUtil.blankToDefault(answer, "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?m)^\\s*(?:#{1,6}\\s*)", "")
                .replaceAll("(?m)^\\s*(?:\\d+[.、)]|[-*•])\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return shortenForChatBubble(cleaned, containsChinese(message) ? 160 : 260);
    }

    private String shortenForChatBubble(String answer, int maxLength) {
        if (answer.length() <= maxLength) {
            return answer;
        }
        int sentenceEnd = Math.max(
                Math.max(answer.lastIndexOf("。", maxLength), answer.lastIndexOf("！", maxLength)),
                Math.max(answer.lastIndexOf("？", maxLength), answer.lastIndexOf(".", maxLength))
        );
        if (sentenceEnd >= Math.min(60, maxLength / 2)) {
            return answer.substring(0, sentenceEnd + 1).trim();
        }
        return answer.substring(0, maxLength - 1).trim() + "…";
    }

    private String buildAnswer(String message, int count, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        String knowledgeSummary = buildKnowledgeSummary(knowledgeMatches);
        boolean chinese = containsChinese(message);
        if (count == 0) {
            if (!knowledgeSummary.isEmpty()) {
                return knowledgeSummary;
            }
            if (chinese) {
                return "暂时没有找到和“" + message + "”匹配的上架商品。可以放宽房间、风格或品类再试试。";
            }
            return "I could not find matching live products for \"" + message + "\" yet. Try a broader room, style or category.";
        }
        String productAnswer = chinese
                ? "我从当前商品库里找到了 " + count + " 个和“" + message + "”相关的上架家具。"
                : "I found " + count + " live furniture products for \"" + message + "\" from the current catalog.";
        return knowledgeSummary.isEmpty() ? productAnswer : productAnswer + " " + knowledgeSummary;
    }

    private boolean containsChinese(String text) {
        return StrUtil.blankToDefault(text, "").chars()
                .anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
    }

    private String buildKnowledgeSummary(List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        return knowledgeMatches.stream()
                .map(FurnitureAssistantKnowledgeMatch::getContent)
                .collect(Collectors.joining(" "));
    }

    private FurnitureAssistantChatRespVO.Source source(String type, String name) {
        FurnitureAssistantChatRespVO.Source source = new FurnitureAssistantChatRespVO.Source();
        source.setType(type);
        source.setName(name);
        return source;
    }

    private List<FurnitureAssistantChatRespVO.Source> buildSources(boolean includeProductSource,
                                                                   List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                                                                   boolean includeModelSource) {
        List<FurnitureAssistantChatRespVO.Source> sources = new ArrayList<>();
        if (includeProductSource) {
            sources.add(source("product-api", "Yudao Product SPU"));
        }
        if (includeModelSource) {
            sources.add(source("model", aiClient.getSourceName()));
        }
        knowledgeMatches.stream()
                .map(match -> source(match.getType(), match.getName()))
                .forEach(sources::add);
        return sources;
    }

    @lombok.Value
    private static class AiAnswer {
        String answer;
        boolean modelBacked;

        static AiAnswer fallback(String answer) {
            return new AiAnswer(answer, false);
        }

        static AiAnswer model(String answer) {
            return new AiAnswer(answer, true);
        }
    }

}
