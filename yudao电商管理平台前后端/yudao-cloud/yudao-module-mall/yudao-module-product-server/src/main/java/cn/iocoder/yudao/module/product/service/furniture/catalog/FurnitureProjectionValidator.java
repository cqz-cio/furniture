package cn.iocoder.yudao.module.product.service.furniture.catalog;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FurnitureProjectionValidator {

    private static final Set<String> CATEGORIES = set(Arrays.asList(
            "sofa", "single-chair", "dining-table", "coffee-table", "bed", "desk",
            "bedroom-storage", "wardrobe", "side-table", "rug", "lighting", "media-storage"));
    private static final Set<String> STYLES = set(Arrays.asList(
            "modern", "cream-style", "natural", "light-luxury", "marble-look"));
    private static final Set<String> COLORS = set(Arrays.asList(
            "cream", "light-gray", "gray", "deep-brown", "dark", "black", "natural", "white"));
    private static final Set<String> MATERIALS = set(Arrays.asList(
            "fabric", "solid-wood", "engineered-wood", "metal", "glass", "leather", "marble-look", "wool"));
    private static final Set<String> ROOMS = set(Arrays.asList(
            "living-room", "dining-room", "bedroom", "children-room", "home-office", "rental-apartment"));
    private static final Set<String> FEATURES = set(Arrays.asList(
            "rounded-edges", "shallow-depth", "compact", "modular", "storage"));

    public static void validate(String category, List<String> styles, String color,
                                List<String> materials, Integer seats, Integer width,
                                Integer depth, Integer height, List<String> rooms,
                                List<String> features) {
        requireCode(CATEGORIES, category, "category");
        requireCodes(STYLES, styles, "styles");
        if (color != null) {
            requireCode(COLORS, color, "color");
        }
        requireCodes(MATERIALS, materials, "materials");
        requireCodes(ROOMS, rooms, "rooms");
        requireCodes(FEATURES, features, "features");
        requirePositive(seats, "seatCount");
        requirePositive(width, "widthMm");
        requirePositive(depth, "depthMm");
        requirePositive(height, "heightMm");
    }

    private static Set<String> set(Collection<String> values) {
        return Collections.unmodifiableSet(new HashSet<>(values));
    }

    private static void requireCodes(Set<String> allowed, List<String> values, String field) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            requireCode(allowed, value, field);
        }
    }

    private static void requireCode(Set<String> allowed, String value, String field) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("Unknown " + field + ": " + value);
        }
    }

    private static void requirePositive(Integer value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private FurnitureProjectionValidator() {
    }

}
