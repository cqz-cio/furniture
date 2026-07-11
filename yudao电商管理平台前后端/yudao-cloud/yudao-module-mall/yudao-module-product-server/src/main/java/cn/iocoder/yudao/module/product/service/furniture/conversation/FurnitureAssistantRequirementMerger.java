package cn.iocoder.yudao.module.product.service.furniture.conversation;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FurnitureAssistantRequirementMerger {

    private static final Pattern MAX_BUDGET = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:元)?\\s*(?:以内|以下|之内|不超过)");

    public MergeResult merge(FurnitureAssistantConversation conversation, String message) {
        String text = StrUtil.blankToDefault(message, "").toLowerCase();
        FurnitureAssistantRequirements requirements = conversation.getRequirements();
        if (containsAny(text, "沙发", "sofa")) requirements.setCategory("sofa");
        if (containsAny(text, "床", "bed")) requirements.setCategory("bed");
        if (containsAny(text, "餐桌", "dining table")) requirements.setCategory("dining table");
        addIfPresent(requirements.getMaterials(), text, "布艺", "真皮", "实木", "岩板");
        addIfPresent(requirements.getStyles(), text, "奶油风", "现代简约", "原木风", "轻奢");
        addIfPresent(requirements.getColors(), text, "米白色", "白色", "黑色", "原木色");
        if (containsAny(text, "有猫", "有狗", "宠物", "pet")) requirements.setHasPets(true);
        if (containsAny(text, "有孩子", "儿童", "小孩")) requirements.setHasChildren(true);
        Matcher budget = MAX_BUDGET.matcher(text);
        if (budget.find()) requirements.setBudgetMax(new BigDecimal(budget.group(1)));
        applyOrdinalExclusion(conversation, text);
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
        }
    }

    private void addIfPresent(List<String> values, String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                values.clear();
                values.add(candidate);
                return;
            }
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
