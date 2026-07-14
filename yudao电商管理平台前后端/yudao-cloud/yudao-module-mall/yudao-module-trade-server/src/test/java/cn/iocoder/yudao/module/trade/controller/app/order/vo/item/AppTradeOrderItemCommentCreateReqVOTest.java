package cn.iocoder.yudao.module.trade.controller.app.order.vo.item;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppTradeOrderItemCommentCreateReqVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testValidate_rejectsBlankCommentContent() {
        AppTradeOrderItemCommentCreateReqVO reqVO = buildReqVO("   ");

        Set<ConstraintViolation<AppTradeOrderItemCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "content".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_rejectsDescriptionScoresBelowRange() {
        AppTradeOrderItemCommentCreateReqVO reqVO = buildReqVO("鍟嗗搧婊℃剰");
        reqVO.setDescriptionScores(0);

        Set<ConstraintViolation<AppTradeOrderItemCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "descriptionScores".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_rejectsBenefitScoresAboveRange() {
        AppTradeOrderItemCommentCreateReqVO reqVO = buildReqVO("鍟嗗搧婊℃剰");
        reqVO.setBenefitScores(6);

        Set<ConstraintViolation<AppTradeOrderItemCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "benefitScores".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_acceptsValidRequest() {
        AppTradeOrderItemCommentCreateReqVO reqVO = buildReqVO("鍟嗗搧婊℃剰");

        Set<ConstraintViolation<AppTradeOrderItemCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    private static AppTradeOrderItemCommentCreateReqVO buildReqVO(String content) {
        AppTradeOrderItemCommentCreateReqVO reqVO = new AppTradeOrderItemCommentCreateReqVO();
        reqVO.setAnonymous(Boolean.FALSE);
        reqVO.setOrderItemId(201L);
        reqVO.setDescriptionScores(5);
        reqVO.setBenefitScores(5);
        reqVO.setContent(content);
        reqVO.setPicUrls(Collections.emptyList());
        return reqVO;
    }

}
