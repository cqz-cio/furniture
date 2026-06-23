package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalAddressVerificationProvider implements AddressVerificationProvider {

    private static final String SOURCE = "backend-address-verification";
    private static final Pattern ZIP_PATTERN = Pattern.compile("\\d{5}");

    @Override
    public AppAddressVerifyRespVO verify(AppAddressVerifyReqVO reqVO) {
        AppAddressVerifyReqVO.Address address = reqVO == null ? null : reqVO.getAddress();
        AppAddressVerifyRespVO respVO = new AppAddressVerifyRespVO();
        respVO.setSource(SOURCE);
        respVO.setOriginalAddress(getOriginalAddress(reqVO, address));

        if (!hasRequiredFields(address)) {
            respVO.setStatus("unverified");
            respVO.setReason("missing-required-fields");
            respVO.setRequiresConfirmation(true);
            return respVO;
        }

        AppAddressVerifyReqVO.Address suggestedAddress = normalizeAddress(address);
        boolean changed = !addressEquals(address, suggestedAddress);
        respVO.setStatus(changed ? "suggested" : "verified");
        respVO.setReason(changed ? "backend-standardized" : "");
        respVO.setRequiresConfirmation(changed);
        respVO.setSuggestedAddress(suggestedAddress);
        return respVO;
    }

    @Override
    public AppAddressVerificationStatusRespVO.ProviderStatus getStatus() {
        AppAddressVerificationStatusRespVO.ProviderStatus status = new AppAddressVerificationStatusRespVO.ProviderStatus();
        status.setSource(SOURCE);
        status.setName("Backend fallback verification");
        status.setEnabled(true);
        status.setFallback(true);
        status.setReason("normalization-only");
        return status;
    }

    private static Object getOriginalAddress(AppAddressVerifyReqVO reqVO, AppAddressVerifyReqVO.Address address) {
        Map<String, Object> localVerification = reqVO == null ? null : reqVO.getLocalVerification();
        if (localVerification != null && localVerification.get("originalAddress") != null) {
            return localVerification.get("originalAddress");
        }
        return address;
    }

    private static boolean hasRequiredFields(AppAddressVerifyReqVO.Address address) {
        return address != null
                && hasText(address.getFirstName())
                && hasText(address.getLastName())
                && hasText(address.getStreet())
                && hasText(address.getCity())
                && hasText(address.getState())
                && hasText(address.getPostalCode())
                && hasText(address.getPhone());
    }

    private static AppAddressVerifyReqVO.Address normalizeAddress(AppAddressVerifyReqVO.Address address) {
        AppAddressVerifyReqVO.Address normalized = new AppAddressVerifyReqVO.Address();
        normalized.setFirstName(clean(address.getFirstName()));
        normalized.setLastName(clean(address.getLastName()));
        normalized.setStreet(normalizeStreet(address.getStreet()));
        normalized.setApartment(clean(address.getApartment()));
        normalized.setCity(clean(address.getCity()));
        normalized.setState(clean(address.getState()).toUpperCase());
        normalized.setPostalCode(normalizePostalCode(address.getPostalCode()));
        normalized.setPhone(clean(address.getPhone()));
        normalized.setCountry(hasText(address.getCountry()) ? clean(address.getCountry()) : "United States");
        return normalized;
    }

    private static String normalizeStreet(String value) {
        String street = clean(value).replaceAll("\\s+", " ").toUpperCase();
        street = street.replaceAll("\\bSTREET\\b", "ST");
        street = street.replaceAll("\\bAVENUE\\b", "AVE");
        street = street.replaceAll("\\bROAD\\b", "RD");
        street = street.replaceAll("\\bPARKWAY\\b", "PKWY");
        street = street.replaceAll("\\bDRIVE\\b", "DR");
        street = street.replaceAll("\\bLANE\\b", "LN");
        street = street.replaceAll("\\bBOULEVARD\\b", "BLVD");
        return street;
    }

    private static String normalizePostalCode(String value) {
        Matcher matcher = ZIP_PATTERN.matcher(clean(value));
        return matcher.find() ? matcher.group() : "";
    }

    private static boolean addressEquals(AppAddressVerifyReqVO.Address left, AppAddressVerifyReqVO.Address right) {
        return Objects.equals(clean(left.getFirstName()), right.getFirstName())
                && Objects.equals(clean(left.getLastName()), right.getLastName())
                && Objects.equals(clean(left.getStreet()), right.getStreet())
                && Objects.equals(clean(left.getApartment()), right.getApartment())
                && Objects.equals(clean(left.getCity()), right.getCity())
                && Objects.equals(clean(left.getState()), right.getState())
                && Objects.equals(clean(left.getPostalCode()), right.getPostalCode())
                && Objects.equals(clean(left.getPhone()), right.getPhone())
                && Objects.equals(hasText(left.getCountry()) ? clean(left.getCountry()) : "United States", right.getCountry());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

}
