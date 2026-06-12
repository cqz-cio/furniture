package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;

import java.util.List;
import java.util.stream.Collectors;

public final class FurnitureAssistantPromptBuilder {

    private FurnitureAssistantPromptBuilder() {
    }

    public static String buildUserPrompt(FurnitureAssistantAiRequest request) {
        return "Customer request:\n" + request.getMessage() + "\n\n"
                + "Fallback answer if model context is insufficient:\n" + request.getFallbackAnswer() + "\n\n"
                + "Returned product list from commerce system:\n" + buildProductContext(request.getProducts()) + "\n\n"
                + "Knowledge snippets:\n" + buildKnowledgeContext(request.getKnowledgeMatches()) + "\n\n"
                + "Response rules:\n"
                + "- Commerce product search has already run before this prompt when product intent was detected.\n"
                + "- Write one concise storefront answer in the same language as the customer request.\n"
                + "- Do not use Markdown, bold markers, numbered lists or bullet lists.\n"
                + "- Keep the chat answer within two short sentences; the product cards carry detailed product data.\n"
                + "- Rank the strongest match first when products are returned.\n"
                + "- Compare useful trade-offs by budget, room, style, material, comfort, stock and membership value when the data is available.\n"
                + "- Explain why the recommended product fits; mention price and stock only from the product list.\n"
                + "- If the returned products are only closest matches, say what matches and what may still need confirmation.\n"
                + "- Ask at most one follow-up question when size, room, color, material or budget is unclear.\n"
                + "- When no products are returned, do not name a product; answer only from knowledge snippets or the fallback answer and suggest how to broaden the request.\n"
                + "- Do not recommend products outside the supplied product list.\n"
                + "- Do not invent product names, prices, stock or IDs.\n"
                + "- Do not mention internal source labels, tools, raw JSON, IDs or SKU IDs unless the customer asks.";
    }

    private static String buildProductContext(List<FurnitureAssistantChatRespVO.Product> products) {
        if (products == null || products.isEmpty()) {
            return "No products were returned.";
        }
        return products.stream()
                .map(product -> "- id=" + product.getId()
                        + ", skuId=" + product.getSkuId()
                        + ", name=" + product.getName()
                        + ", subtitle=" + product.getSubtitle()
                        + ", price=" + product.getPrice()
                        + ", marketPrice=" + product.getMarketPrice()
                        + ", stock=" + product.getStock()
                        + ", reason=" + product.getReason())
                .collect(Collectors.joining("\n"));
    }

    private static String buildKnowledgeContext(List<FurnitureAssistantKnowledgeMatch> knowledgeMatches) {
        if (knowledgeMatches == null || knowledgeMatches.isEmpty()) {
            return "No knowledge snippets were matched.";
        }
        return knowledgeMatches.stream()
                .map(match -> "- " + match.getName() + ": " + match.getContent())
                .collect(Collectors.joining("\n"));
    }

}
