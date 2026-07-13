package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantConversationRespVO;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantConversation;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantConversationStore;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirementMerger;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
        FurnitureProductSearchRequest searchRequest = FurnitureProductSearchRequest.from(
                message, conversation.getRequirements(), 3);
        searchRequest.setIncludeAllVariants(true);
        boolean shouldSearchProducts = productSearchTool.shouldSearchProducts(
                message, conversation.getRequirements(), knowledgeMatches);
        FurnitureProductSearchResult searchResult = shouldSearchProducts
                ? productSearchTool.searchProducts(searchRequest) : FurnitureProductSearchResult.none();

        FurnitureAssistantChatRespVO respVO = new FurnitureAssistantChatRespVO();
        List<FurnitureAssistantChatRespVO.Product> products = searchResult.getMatchType() == FurnitureMatchType.NONE
                ? Collections.emptyList() : groupProductsBySpu(searchResult.getProducts());
        products = products.stream().filter(product -> !conversation.getExcludedProductIds().contains(product.getId()))
                .collect(Collectors.toList());
        String fallbackAnswer = buildAnswer(message, products.size(), knowledgeMatches, shouldSearchProducts,
                searchResult.getMatchType(), searchResult.getMatchedConstraints(), searchResult.getUnmetConstraints());
        AiAnswer aiAnswer = buildAiBackedAnswer(message, fallbackAnswer, products, knowledgeMatches, conversation,
                searchResult);
        respVO.setAnswer(aiAnswer.getAnswer());
        respVO.setProducts(products);
        respVO.setMatchType(searchResult.getMatchType());
        respVO.setMatchedConstraints(searchResult.getMatchedConstraints());
        respVO.setUnmetConstraints(searchResult.getUnmetConstraints());
        respVO.setSources(buildSources(shouldSearchProducts, knowledgeMatches, aiAnswer.isModelBacked(),
                aiAnswer.isModelFailure()));
        respVO.setConversationId(conversationId);
        respVO.setRequirements(conversation.getRequirements());
        respVO.setMissingFields(Collections.emptyList());
        conversation.appendMessage("assistant", respVO.getAnswer());
        conversation.setLastRecommendations(products.stream()
                .map(product -> new FurnitureAssistantConversation.RecommendationRef(
                        product.getId(), product.getSkuId(), product.getPrice()))
                .collect(Collectors.toList()));
        conversation.setLastProducts(new ArrayList<>(products));
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
        response.setProducts(value.getLastProducts());
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

    private AiAnswer buildAiBackedAnswer(String message, String fallbackAnswer,
                                         List<FurnitureAssistantChatRespVO.Product> products,
                                         List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                                         FurnitureAssistantConversation conversation,
                                         FurnitureProductSearchResult searchResult) {
        if (!aiClient.isEnabled()) {
            return AiAnswer.modelFailure(fallbackAnswer);
        }
        try {
            String aiAnswer = aiClient.generateAnswer(new FurnitureAssistantAiRequest(
                    message, fallbackAnswer, products, knowledgeMatches, buildConversationContext(conversation),
                    searchResult.getMatchType(), searchResult.getMatchedConstraints(),
                    searchResult.getUnmetConstraints()));
            if (StrUtil.isBlank(aiAnswer)) {
                return AiAnswer.modelFailure(fallbackAnswer);
            }
            return AiAnswer.model(prepareDisplayAnswer(aiAnswer, message));
        } catch (Exception ex) {
            log.warn("[buildAiBackedAnswer][AI answer generation failed, fallback to deterministic answer]", ex);
            return AiAnswer.modelFailure(fallbackAnswer);
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

    private String buildAnswer(String message, int count, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches,
                               boolean productIntent, FurnitureMatchType matchType,
                               List<String> matchedConstraints, List<String> unmetConstraints) {
        String knowledgeSummary = buildKnowledgeSummary(knowledgeMatches);
        boolean chinese = containsChinese(message);
        if (productIntent && matchType == FurnitureMatchType.NONE) {
            String gaps = formatConstraints(unmetConstraints);
            return chinese
                    ? "当前没有商品满足全部条件" + (gaps.isEmpty() ? "" : "（未满足：" + gaps + "）")
                    + "。可以放宽其中一个条件后再试。"
                    : "No catalog product satisfies all requested constraints"
                    + (gaps.isEmpty() ? "" : " (unmet: " + gaps + ")")
                    + ". Try relaxing one constraint and search again.";
        }
        if (productIntent && matchType == FurnitureMatchType.PARTIAL) {
            String matched = formatConstraints(matchedConstraints);
            String gaps = formatConstraints(unmetConstraints);
            return chinese
                    ? "找到了 " + count + " 个最接近的商品"
                    + (matched.isEmpty() ? "" : "，已匹配：" + matched)
                    + (gaps.isEmpty() ? "" : "；未满足：" + gaps) + "。"
                    : "I found " + count + " closest catalog " + (count == 1 ? "product" : "products")
                    + (matched.isEmpty() ? "" : "; matched: " + matched)
                    + (gaps.isEmpty() ? "" : "; unmet: " + gaps) + ".";
        }
        if (count == 0) {
            if (!knowledgeSummary.isEmpty()) {
                return knowledgeSummary;
            }
            if (!productIntent) {
                if (containsAny(message, "说中文", "中文回答", "speak chinese")) {
                    return "好的，接下来我会使用中文。你想为哪个房间挑选家具？";
                }
                if (containsAny(message, "你好", "您好", "hello", "hi")) {
                    return containsChinese(message) ? "你好，我可以帮你挑选家具、比较商品或解答配送售后问题。"
                            : "Hello, I can help you choose furniture, compare products, or answer delivery questions.";
                }
                return containsChinese(message) ? "请告诉我你想选购的家具、房间、预算或风格。"
                        : "Tell me what furniture, room, budget, or style you have in mind.";
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

    private String formatConstraints(List<String> constraints) {
        return constraints == null || constraints.isEmpty() ? "" : String.join(", ", constraints);
    }

    private List<FurnitureAssistantChatRespVO.Product> groupProductsBySpu(
            List<FurnitureAssistantChatRespVO.Product> concreteProducts) {
        Map<Long, FurnitureAssistantChatRespVO.Product> productsBySpu = new LinkedHashMap<>();
        if (concreteProducts == null) return Collections.emptyList();
        for (FurnitureAssistantChatRespVO.Product concrete : concreteProducts) {
            if (concrete == null || concrete.getId() == null) continue;
            FurnitureAssistantChatRespVO.Product primary = productsBySpu.get(concrete.getId());
            if (primary == null) {
                primary = concrete;
                primary.setVariants(new ArrayList<>());
                if (primary.getSkuProperties() == null) primary.setSkuProperties(Collections.emptyList());
                productsBySpu.put(primary.getId(), primary);
            }
            primary.getVariants().add(toVariant(concrete));
        }
        return new ArrayList<>(productsBySpu.values());
    }

    private FurnitureAssistantChatRespVO.SkuVariant toVariant(FurnitureAssistantChatRespVO.Product concrete) {
        FurnitureAssistantChatRespVO.SkuVariant variant = new FurnitureAssistantChatRespVO.SkuVariant();
        variant.setSkuId(concrete.getSkuId());
        variant.setSkuProperties(concrete.getSkuProperties() == null
                ? Collections.emptyList() : new ArrayList<>(concrete.getSkuProperties()));
        variant.setPrice(concrete.getPrice());
        variant.setStock(concrete.getStock());
        return variant;
    }

    private String buildConversationContext(FurnitureAssistantConversation conversation) {
        if (conversation == null || conversation.getMessages().isEmpty()) {
            return "No earlier conversation is available.";
        }
        return conversation.getMessages().stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));
    }

    private boolean containsAny(String text, String... values) {
        String normalized = StrUtil.blankToDefault(text, "").toLowerCase();
        for (String value : values) {
            if (normalized.contains(value)) return true;
        }
        return false;
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
                                                                   boolean includeModelSource,
                                                                   boolean includeFallbackSource) {
        List<FurnitureAssistantChatRespVO.Source> sources = new ArrayList<>();
        if (includeProductSource) {
            sources.add(source("product-api", "Yudao Product SPU"));
        }
        if (includeModelSource) {
            sources.add(source("model", aiClient.getSourceName()));
        }
        if (includeFallbackSource) {
            sources.add(source("fallback", "Model unavailable or API key not loaded; deterministic response"));
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
        boolean modelFailure;

        static AiAnswer fallback(String answer) {
            return new AiAnswer(answer, false, false);
        }

        static AiAnswer model(String answer) {
            return new AiAnswer(answer, true, false);
        }

        static AiAnswer modelFailure(String answer) {
            return new AiAnswer(answer, false, true);
        }
    }

}
