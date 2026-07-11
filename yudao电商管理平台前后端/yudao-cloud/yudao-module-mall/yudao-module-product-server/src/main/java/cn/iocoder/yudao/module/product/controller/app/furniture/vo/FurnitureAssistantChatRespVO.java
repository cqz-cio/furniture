package cn.iocoder.yudao.module.product.controller.app.furniture.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import cn.iocoder.yudao.module.product.service.furniture.conversation.FurnitureAssistantRequirements;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "User App - Furniture assistant chat response")
@Data
public class FurnitureAssistantChatRespVO {

    @Schema(description = "Conversation id")
    private String conversationId;

    @Schema(description = "Remembered shopping requirements")
    private FurnitureAssistantRequirements requirements;

    @Schema(description = "Important fields still missing")
    private List<String> missingFields;

    @Schema(description = "Assistant answer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answer;

    @Schema(description = "Structured product recommendations", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Product> products;

    @Schema(description = "Information sources", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Source> sources;

    @Schema(description = "Furniture assistant product recommendation")
    @Data
    public static class Product {

        @Schema(description = "SPU id", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @Schema(description = "SKU id")
        private Long skuId;

        @Schema(description = "Product name", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "Product subtitle")
        private String subtitle;

        @Schema(description = "Price in yuan", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal price;

        @Schema(description = "Market price in yuan")
        private BigDecimal marketPrice;

        @Schema(description = "Stock", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer stock;

        @Schema(description = "Cover image")
        private String cover;

        @Schema(description = "Recommendation reason", requiredMode = Schema.RequiredMode.REQUIRED)
        private String reason;

        @Schema(description = "Frontend detail URL", requiredMode = Schema.RequiredMode.REQUIRED)
        private String detailUrl;

    }

    @Schema(description = "Furniture assistant source")
    @Data
    public static class Source {

        @Schema(description = "Source type", requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        @Schema(description = "Source name", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

    }

}
