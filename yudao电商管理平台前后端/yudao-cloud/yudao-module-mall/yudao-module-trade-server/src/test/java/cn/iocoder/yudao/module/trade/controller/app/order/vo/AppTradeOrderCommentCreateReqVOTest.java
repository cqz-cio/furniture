package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppTradeOrderCommentCreateReqVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testValidate_rejectsBlankCommentContent() {
        AppTradeOrderCommentCreateReqVO reqVO = buildReqVO("   ");

        Set<ConstraintViolation<AppTradeOrderCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "items[0].content".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_acceptsNonBlankCommentContent() {
        AppTradeOrderCommentCreateReqVO reqVO = buildReqVO("整体满意");

        Set<ConstraintViolation<AppTradeOrderCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void testValidate_rejectsDescriptionScoresBelowRange() {
        AppTradeOrderCommentCreateReqVO reqVO = buildReqVO("鏁翠綋婊℃剰");
        reqVO.getItems().get(0).setDescriptionScores(0);

        Set<ConstraintViolation<AppTradeOrderCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "items[0].descriptionScores".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_rejectsBenefitScoresAboveRange() {
        AppTradeOrderCommentCreateReqVO reqVO = buildReqVO("鏁翠綋婊℃剰");
        reqVO.getItems().get(0).setBenefitScores(6);

        Set<ConstraintViolation<AppTradeOrderCommentCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "items[0].benefitScores".equals(violation.getPropertyPath().toString())));
    }

    private static AppTradeOrderCommentCreateReqVO buildReqVO(String content) {
        AppTradeOrderCommentCreateReqVO.Item item = new AppTradeOrderCommentCreateReqVO.Item();
        item.setOrderItemId(201L);
        item.setDescriptionScores(5);
        item.setBenefitScores(5);
        item.setContent(content);
        item.setPicUrls(Collections.emptyList());

        AppTradeOrderCommentCreateReqVO reqVO = new AppTradeOrderCommentCreateReqVO();
        reqVO.setOrderId(100L);
        reqVO.setAnonymous(Boolean.FALSE);
        reqVO.setItems(Collections.singletonList(item));
        return reqVO;
    }

}
