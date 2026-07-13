package cn.iocoder.yudao.module.product.service.furniture.conversation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class FurnitureRequirementPatch {

    private final Set<String> mentionedFields = new LinkedHashSet<>();

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

    public boolean mentions(String field) {
        return mentionedFields.contains(field);
    }

    public void mention(String field) {
        mentionedFields.add(field);
    }

    public void applyTo(FurnitureAssistantRequirements target) {
        if (mentions("category")) target.setCategory(category);
        if (mentions("budgetMin")) target.setBudgetMin(budgetMin);
        if (mentions("budgetMax")) target.setBudgetMax(budgetMax);
        if (mentions("styles")) target.setStyles(copy(styles));
        if (mentions("colors")) target.setColors(copy(colors));
        if (mentions("materials")) target.setMaterials(copy(materials));
        if (mentions("excludedMaterials")) target.setExcludedMaterials(copy(excludedMaterials));
        if (mentions("roomTypes")) target.setRoomTypes(copy(roomTypes));
        if (mentions("roomSize")) target.setRoomSize(roomSize);
        if (mentions("roomWidthMm")) target.setRoomWidthMm(roomWidthMm);
        if (mentions("roomDepthMm")) target.setRoomDepthMm(roomDepthMm);
        if (mentions("maxWidthMm")) target.setMaxWidthMm(maxWidthMm);
        if (mentions("maxDepthMm")) target.setMaxDepthMm(maxDepthMm);
        if (mentions("maxHeightMm")) target.setMaxHeightMm(maxHeightMm);
        if (mentions("seatCount")) target.setSeatCount(seatCount);
        if (mentions("hasChildren")) target.setHasChildren(hasChildren);
        if (mentions("hasPets")) target.setHasPets(hasPets);
        if (mentions("easyClean")) target.setEasyClean(easyClean);
        if (mentions("scratchResistant")) target.setScratchResistant(scratchResistant);
        if (mentions("movable")) target.setMovable(movable);
        if (mentions("rentalFriendly")) target.setRentalFriendly(rentalFriendly);
        if (mentions("preferredFeatures")) target.setPreferredFeatures(copy(preferredFeatures));
        mergeConstraintPresence(target.getHardConstraints(), hardConstraints);
        mergeConstraintPresence(target.getNonRelaxableConstraints(), nonRelaxableConstraints);
    }

    private void mergeConstraintPresence(Set<String> target, Set<String> patchConstraints) {
        for (String field : mentionedFields) {
            target.remove(field);
            if (patchConstraints.contains(field)) target.add(field);
        }
    }

    private List<String> copy(List<String> values) {
        return values == null ? new ArrayList<String>() : new ArrayList<>(values);
    }
}
