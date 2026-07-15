package cn.iocoder.yudao.module.product.api.comment.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductCommentBatchCreateReqDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testValidate_rejectsEmptyComments() {
        ProductCommentBatchCreateReqDTO reqDTO = new ProductCommentBatchCreateReqDTO();
        reqDTO.setComments(Collections.emptyList());

        Set<ConstraintViolation<ProductCommentBatchCreateReqDTO>> violations = validator.validate(reqDTO);

        assertTrue(violations.stream().anyMatch(violation -> "comments".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_appliesNestedValidation() {
        ProductCommentCreateReqDTO comment = new ProductCommentCreateReqDTO();
        comment.setSkuId(101L);
        comment.setUserId(10L);
        comment.setOrderId(100L);
        comment.setOrderItemId(201L);
        comment.setAnonymous(Boolean.FALSE);
        comment.setDescriptionScores(5);
        comment.setBenefitScores(5);
        comment.setContent("   ");
        ProductCommentBatchCreateReqDTO reqDTO = new ProductCommentBatchCreateReqDTO();
        reqDTO.setComments(Collections.singletonList(comment));

        Set<ConstraintViolation<ProductCommentBatchCreateReqDTO>> violations = validator.validate(reqDTO);

        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().contains("content")));
    }

    @Test
    public void testValidate_acceptsValidComments() {
        ProductCommentCreateReqDTO comment = new ProductCommentCreateReqDTO();
        comment.setSkuId(101L);
        comment.setUserId(10L);
        comment.setOrderId(100L);
        comment.setOrderItemId(201L);
        comment.setAnonymous(Boolean.FALSE);
        comment.setDescriptionScores(5);
        comment.setBenefitScores(5);
        comment.setContent("整体满意");
        ProductCommentBatchCreateReqDTO reqDTO = new ProductCommentBatchCreateReqDTO();
        reqDTO.setComments(Collections.singletonList(comment));

        Set<ConstraintViolation<ProductCommentBatchCreateReqDTO>> violations = validator.validate(reqDTO);

        assertTrue(violations.isEmpty());
    }

}
