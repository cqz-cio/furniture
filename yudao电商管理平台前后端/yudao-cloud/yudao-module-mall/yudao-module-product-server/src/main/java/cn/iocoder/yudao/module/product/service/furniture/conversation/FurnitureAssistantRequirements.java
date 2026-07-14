package cn.iocoder.yudao.module.product.service.furniture.conversation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class FurnitureAssistantRequirements {

    private String category;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private List<String> styles = new ArrayList<>();
    private List<String> colors = new ArrayList<>();
    private List<String> materials = new ArrayList<>();
    private List<String> excludedMaterials = new ArrayList<>();
    private List<String> roomTypes = new ArrayList<>();
    private BigDecimal roomSize;
    private Integer roomWidthMm;
    private Integer roomDepthMm;
    private Integer maxWidthMm;
    private Integer maxDepthMm;
    private Integer maxHeightMm;
    private Integer seatCount;
    private Boolean hasChildren;
    private Boolean hasPets;
    private Boolean easyClean;
    private Boolean scratchResistant;
    private Boolean movable;
    private Boolean rentalFriendly;
    private List<String> preferredFeatures = new ArrayList<>();
    private Set<String> hardConstraints = new LinkedHashSet<>();
    private Set<String> nonRelaxableConstraints = new LinkedHashSet<>();

}
