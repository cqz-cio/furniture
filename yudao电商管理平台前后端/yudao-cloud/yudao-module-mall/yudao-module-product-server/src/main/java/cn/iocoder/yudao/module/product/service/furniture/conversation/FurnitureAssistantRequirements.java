package cn.iocoder.yudao.module.product.service.furniture.conversation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class FurnitureAssistantRequirements {

    private String category;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> styles = new ArrayList<>();
    private List<String> colors = new ArrayList<>();
    private List<String> materials = new ArrayList<>();
    private BigDecimal roomSize;
    private Integer seatCount;
    private Boolean hasChildren;
    private Boolean hasPets;
    private List<String> preferredFeatures = new ArrayList<>();

}
