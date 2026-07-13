package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureRequirementNormalizerTest {

    private final FurnitureRequirementNormalizer normalizer = new FurnitureRequirementNormalizer();

    @Test
    void normalize_shouldExtractChineseSofaConstraints() {
        FurnitureRequirementPatch value = normalizer.normalize(
                "我想买一张8000元以内、适合小客厅、宽度不超过220厘米的浅灰色三人布艺沙发。");

        assertEquals("sofa", value.getCategory());
        assertEquals(new BigDecimal("8000"), value.getBudgetMax());
        assertEquals(Integer.valueOf(2200), value.getMaxWidthMm());
        assertEquals(Integer.valueOf(3), value.getSeatCount());
        assertEquals(Collections.singletonList("light-gray"), value.getColors());
        assertEquals(Collections.singletonList("fabric"), value.getMaterials());
        assertTrue(value.getHardConstraints().containsAll(
                Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));
    }

    @Test
    void normalize_shouldExtractEnglishRentalStorageConstraints() {
        FurnitureRequirementPatch value = normalizer.normalize(
                "I need movable bedroom storage under 150 cm wide for a rental apartment.");

        assertEquals("bedroom-storage", value.getCategory());
        assertEquals(Integer.valueOf(1500), value.getMaxWidthMm());
        assertNull(value.getBudgetMax());
        assertEquals(Boolean.TRUE, value.getMovable());
        assertEquals(Boolean.TRUE, value.getRentalFriendly());
        assertTrue(value.getHardConstraints().containsAll(
                Arrays.asList("maxWidthMm", "movable", "rentalFriendly")));
    }

    @Test
    void normalize_shouldConvertSupportedDimensionsToMillimeters() {
        assertEquals(Integer.valueOf(2200), normalizer.normalize("宽度不超过2.2米").getMaxWidthMm());
        assertEquals(Integer.valueOf(2200), normalizer.normalize("宽度不超过220厘米").getMaxWidthMm());
        assertEquals(Integer.valueOf(2200), normalizer.normalize("under 220 cm wide").getMaxWidthMm());
        assertEquals(Integer.valueOf(2200), normalizer.normalize("under 2200 mm wide").getMaxWidthMm());
    }

    @Test
    void normalize_shouldMarkMustOnlyAndExclusionAsNonRelaxable() {
        FurnitureRequirementPatch value = normalizer.normalize("只要实木餐桌，不要真皮。");

        assertEquals("dining-table", value.getCategory());
        assertEquals(Collections.singletonList("solid-wood"), value.getMaterials());
        assertEquals(Collections.singletonList("leather"), value.getExcludedMaterials());
        assertTrue(value.getNonRelaxableConstraints().contains("materials"));
        assertTrue(value.getNonRelaxableConstraints().contains("excludedMaterials"));
    }

    @Test
    void normalize_shouldRepresentColorRetractionAsAnExplicitClear() {
        FurnitureRequirementPatch value = normalizer.normalize("颜色不限了");

        assertTrue(value.mentions("colors"));
        assertTrue(value.getColors().isEmpty());
    }
}
