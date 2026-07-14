package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppTradeOrderCreateReqVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testValidate_expressOrderRequiresAddressVerificationAudit() {
        AppTradeOrderCreateReqVO reqVO = buildReqVO(DeliveryTypeEnum.EXPRESS.getType());

        Set<ConstraintViolation<AppTradeOrderCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "confirmedAddressVerificationForExpress".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_expressOrderAcceptsConfirmedAddressVerificationAudit() {
        AppTradeOrderCreateReqVO reqVO = buildReqVO(DeliveryTypeEnum.EXPRESS.getType());
        reqVO.setAddressVerification(buildAddressVerification());

        Set<ConstraintViolation<AppTradeOrderCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void testValidate_expressOrderRequiresConfirmedSelectedAddressFields() {
        AppTradeOrderCreateReqVO reqVO = buildReqVO(DeliveryTypeEnum.EXPRESS.getType());
        Map<String, Object> addressVerification = buildAddressVerification();
        Map<String, Object> selectedAddress = new HashMap<>();
        selectedAddress.put("street", "1600 AMPHITHEATRE PKWY");
        addressVerification.put("selectedAddress", selectedAddress);
        reqVO.setAddressVerification(addressVerification);

        Set<ConstraintViolation<AppTradeOrderCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "confirmedAddressVerificationForExpress".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_expressOrderRejectsUnsupportedAddressVerificationValues() {
        AppTradeOrderCreateReqVO reqVO = buildReqVO(DeliveryTypeEnum.EXPRESS.getType());
        Map<String, Object> addressVerification = buildAddressVerification();
        addressVerification.put("source", "trusted-because-user-said-so");
        addressVerification.put("addressSource", "legacy");
        addressVerification.put("status", "maybe");
        addressVerification.put("choice", "skip-review");
        reqVO.setAddressVerification(addressVerification);

        Set<ConstraintViolation<AppTradeOrderCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "confirmedAddressVerificationForExpress".equals(violation.getPropertyPath().toString())));
    }

    @Test
    public void testValidate_pickUpOrderDoesNotRequireAddressVerificationAudit() {
        AppTradeOrderCreateReqVO reqVO = buildReqVO(DeliveryTypeEnum.PICK_UP.getType());

        Set<ConstraintViolation<AppTradeOrderCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    private static AppTradeOrderCreateReqVO buildReqVO(Integer deliveryType) {
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(100L);
        item.setCount(1);

        AppTradeOrderCreateReqVO reqVO = new AppTradeOrderCreateReqVO();
        reqVO.setItems(java.util.Collections.singletonList(item));
        reqVO.setPointStatus(false);
        reqVO.setDeliveryType(deliveryType);
        reqVO.setAddressId(200L);
        return reqVO;
    }

    private static Map<String, Object> buildAddressVerification() {
        Map<String, Object> selectedAddress = new HashMap<>();
        selectedAddress.put("street", "1600 AMPHITHEATRE PKWY");
        selectedAddress.put("city", "Mountain View");
        selectedAddress.put("state", "CA");
        selectedAddress.put("postalCode", "94043");

        Map<String, Object> addressVerification = new HashMap<>();
        addressVerification.put("source", "google-address-validation");
        addressVerification.put("addressSource", "saved");
        addressVerification.put("status", "verified");
        addressVerification.put("choice", "original");
        addressVerification.put("confirmedAt", "2026-06-16T10:00:00.000Z");
        addressVerification.put("selectedAddress", selectedAddress);
        return addressVerification;
    }

}
