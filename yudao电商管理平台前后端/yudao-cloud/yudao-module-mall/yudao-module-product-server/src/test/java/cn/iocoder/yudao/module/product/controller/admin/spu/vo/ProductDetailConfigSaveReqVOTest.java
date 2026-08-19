package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDetailConfigSaveReqVOTest {

    @Test
    void shouldAcceptEmptyOptionalFieldsAndACompleteCanonicalDimension() {
        ProductDetailConfigSaveReqVO empty = new ProductDetailConfigSaveReqVO()
                .setFinish("")
                .setPacking("")
                .setHighlights(List.of())
                .setOptionGroups(List.of());
        ProductDetailConfigSaveReqVO complete = new ProductDetailConfigSaveReqVO()
                .setItemNo("VZC0099")
                .setDimension(new ProductDetailConfigSaveReqVO.Dimension()
                        .setShape("rectangular")
                        .setWidth(new BigDecimal("55"))
                        .setDepth(new BigDecimal("54"))
                        .setHeight(new BigDecimal("95"))
                        .setUnit("cm"))
                .setPacking("Ships in two cartons")
                .setHighlights(List.of("Solid oak frame"));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(empty).isEmpty());
            assertTrue(validator.validate(complete).isEmpty());
        }
    }

    @Test
    void shouldRejectIncompleteDimensionsInvalidUnitsAndMalformedArrayElements() {
        ProductDetailConfigSaveReqVO invalid = new ProductDetailConfigSaveReqVO()
                .setDimension(new ProductDetailConfigSaveReqVO.Dimension()
                        .setShape("round")
                        .setHeight(new BigDecimal("78"))
                        .setUnit("inch"))
                .setHighlights(List.of("  "))
                .setOptionGroups(List.of(new ProductDetailConfigSaveReqVO.OptionGroup()
                        .setKey("")
                        .setLabel("Size")
                        .setValues(List.of())));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(invalid).isEmpty());
        }
    }

}
