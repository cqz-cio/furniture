package cn.iocoder.yudao.module.product.api.comment.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductCommentCreateReqDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testValidate_rejectsBlankCommentContent() {
        ProductCommentCreateReqDTO reqDTO = buildReqDTO("   ");

        Set<ConstraintViolation<ProductCommentCreateReqDTO>> violations = validator.validate(reqDTO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "content".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_rejectsDescriptionScoresBelowRange() {
        ProductCommentCreateReqDTO reqDTO = buildReqDTO("鍟嗗搧婊℃剰");
        reqDTO.setDescriptionScores(0);

        Set<ConstraintViolation<ProductCommentCreateReqDTO>> violations = validator.validate(reqDTO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "descriptionScores".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_rejectsBenefitScoresAboveRange() {
        ProductCommentCreateReqDTO reqDTO = buildReqDTO("鍟嗗搧婊℃剰");
        reqDTO.setBenefitScores(6);

        Set<ConstraintViolation<ProductCommentCreateReqDTO>> violations = validator.validate(reqDTO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "benefitScores".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_acceptsValidRequest() {
        ProductCommentCreateReqDTO reqDTO = buildReqDTO("鍟嗗搧婊℃剰");

        Set<ConstraintViolation<ProductCommentCreateReqDTO>> violations = validator.validate(reqDTO);

        assertTrue(violations.isEmpty());
    }

    private static ProductCommentCreateReqDTO buildReqDTO(String content) {
        ProductCommentCreateReqDTO reqDTO = new ProductCommentCreateReqDTO();
        reqDTO.setSkuId(301L);
        reqDTO.setOrderId(100L);
        reqDTO.setOrderItemId(201L);
        reqDTO.setDescriptionScores(5);
        reqDTO.setBenefitScores(5);
        reqDTO.setContent(content);
        reqDTO.setPicUrls(Collections.emptyList());
        reqDTO.setAnonymous(Boolean.FALSE);
        reqDTO.setUserId(10L);
        return reqDTO;
    }

}
