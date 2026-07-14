package cn.iocoder.yudao.module.product.service.furniture;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class FurnitureAssistantKnowledgeServiceImpl implements FurnitureAssistantKnowledgeService {

    private static final List<KnowledgeEntry> ENTRIES = Arrays.asList(
            new KnowledgeEntry("Membership Rules",
                    Arrays.asList("membership", "member", "coupon", "benefit", "price stack", "stack"),
                    "Membership prices can be used with eligible coupons at checkout unless a campaign marks them as exclusive."),
            new KnowledgeEntry("会员权益规则",
                    Arrays.asList("会员", "会员价", "优惠券", "叠加", "权益"),
                    "会员价可以和符合条件的优惠券一起使用，除非活动规则明确标注不可叠加。"),
            new KnowledgeEntry("Delivery And Installation",
                    Arrays.asList("delivery", "install", "installation", "shipping", "large furniture"),
                    "Large furniture delivery should confirm address coverage, elevator access and installation availability before checkout."),
            new KnowledgeEntry("Returns And After-sales",
                    Arrays.asList("return", "exchange", "after-sales", "refund", "support"),
                    "Return and exchange requests should follow the order after-sales flow, and used or installed items may require support review.")
    );

    private final FurnitureAssistantProperties properties;

    public FurnitureAssistantKnowledgeServiceImpl(FurnitureAssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<FurnitureAssistantKnowledgeMatch> search(String message) {
        if (properties.isAiKnowledgeProvider()) {
            throw new IllegalStateException("Furniture assistant real AI provider is not wired yet. "
                    + "Keep yudao.furniture-assistant.knowledge-provider=keyword until yudao-module-ai/RAG is connected.");
        }
        if (message == null) {
            return Collections.emptyList();
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return ENTRIES.stream()
                .filter(entry -> entry.getKeywords().stream().anyMatch(normalized::contains))
                .limit(2)
                .map(entry -> new FurnitureAssistantKnowledgeMatch("knowledge", entry.getName(), entry.getContent()))
                .collect(Collectors.toList());
    }

    @lombok.Value
    private static class KnowledgeEntry {
        String name;
        List<String> keywords;
        String content;
    }

}
