package cn.iocoder.yudao.module.member.controller.app.address.vo;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppAddressCreateReqVOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    public void testValidate_allowsAddressWithoutVerificationAudit() {
        AppAddressCreateReqVO reqVO = buildReqVO();

        Set<ConstraintViolation<AppAddressCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void testValidate_acceptsConfirmedAddressVerificationAudit() {
        AppAddressCreateReqVO reqVO = buildReqVO();
        reqVO.setAddressVerification(buildAddressVerification());

        Set<ConstraintViolation<AppAddressCreateReqVO>> violations = validator.validate(reqVO);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void testValidate_rejectsIncompleteAddressVerificationAudit() {
        AppAddressCreateReqVO reqVO = buildReqVO();
        Map<String, Object> addressVerification = buildAddressVerification();
        Map<String, Object> selectedAddress = new HashMap<>();
        selectedAddress.put("street", "1600 AMPHITHEATRE PKWY");
        addressVerification.put("selectedAddress", selectedAddress);
        reqVO.setAddressVerification(addressVerification);

        Set<ConstraintViolation<AppAddressCreateReqVO>> violations = validator.validate(reqVO);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> "confirmedAddressVerification".equals(violation.getPropertyPath().toString())));
    }

    private static AppAddressCreateReqVO buildReqVO() {
        AppAddressCreateReqVO reqVO = new AppAddressCreateReqVO();
        reqVO.setName("Ada Lovelace");
        reqVO.setMobile("5551234567");
        reqVO.setAreaId(1L);
        reqVO.setDetailAddress("1600 AMPHITHEATRE PKWY, Mountain View, CA 94043");
        reqVO.setDefaultStatus(true);
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
