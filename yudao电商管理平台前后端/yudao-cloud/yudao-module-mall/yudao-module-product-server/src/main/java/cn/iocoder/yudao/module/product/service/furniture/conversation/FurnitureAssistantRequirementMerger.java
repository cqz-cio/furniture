package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class FurnitureAssistantRequirementMerger {

    private static final List<String> CATEGORY_SPECIFIC_FIELDS = Arrays.asList(
            "seatCount", "maxWidthMm", "maxDepthMm", "maxHeightMm", "preferredFeatures");

    private final FurnitureRequirementNormalizer normalizer = new FurnitureRequirementNormalizer();

    public MergeResult merge(FurnitureAssistantConversation conversation, String message) {
        FurnitureRequirementPatch patch = normalizer.normalize(message);
        FurnitureAssistantRequirements target = conversation.getRequirements();
        if (patch.mentions("category") && !Objects.equals(target.getCategory(), patch.getCategory())) {
            target.setCategory(patch.getCategory());
            target.setSeatCount(null);
            target.setMaxWidthMm(null);
            target.setMaxDepthMm(null);
            target.setMaxHeightMm(null);
            target.getPreferredFeatures().clear();
            target.getHardConstraints().removeAll(CATEGORY_SPECIFIC_FIELDS);
            target.getNonRelaxableConstraints().removeAll(CATEGORY_SPECIFIC_FIELDS);
        }
        patch.applyTo(target);
        applyOrdinalExclusion(conversation, message == null ? "" : message.toLowerCase(Locale.ROOT));
        return new MergeResult(false);
    }

    private void applyOrdinalExclusion(FurnitureAssistantConversation conversation, String text) {
        if (!containsAny(text, "第一款", "第一个", "first") || conversation.getLastRecommendations().isEmpty()) return;
        FurnitureAssistantConversation.RecommendationRef selected = conversation.getLastRecommendations().get(0);
        if (!conversation.getExcludedProductIds().contains(selected.getProductId())) {
            conversation.getExcludedProductIds().add(selected.getProductId());
        }
        if (containsAny(text, "便宜", "太贵", "cheaper") && selected.getPrice() != null) {
            conversation.getRequirements().setBudgetMax(selected.getPrice().subtract(BigDecimal.ONE));
            conversation.getRequirements().getHardConstraints().add("budgetMax");
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    public static class MergeResult {
        private final boolean clarificationRequired;

        public MergeResult(boolean clarificationRequired) {
            this.clarificationRequired = clarificationRequired;
        }

        public boolean isClarificationRequired() {
            return clarificationRequired;
        }
    }
}
