package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoogleAddressVerificationProvider implements AddressVerificationProvider {

    private static final String SOURCE = "google-address-validation";
    private static final String DEFAULT_ENDPOINT = "https://addressvalidation.googleapis.com/v1:validateAddress";

    private final String apiKey;
    private final String endpoint;
    private final boolean enableUspsCass;
    private final GoogleAddressValidationClient client;

    public GoogleAddressVerificationProvider(
            @Value("${yudao.member.address-verification.google.api-key:}") String apiKey,
            @Value("${yudao.member.address-verification.google.endpoint:" + DEFAULT_ENDPOINT + "}") String endpoint,
            @Value("${yudao.member.address-verification.google.enable-usps-cass:true}") boolean enableUspsCass,
            GoogleAddressValidationClient client) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.enableUspsCass = enableUspsCass;
        this.client = client;
    }

    @Override
    public AppAddressVerifyRespVO verify(AppAddressVerifyReqVO reqVO) {
        if (!hasText(apiKey)) {
            return null;
        }
        Map<String, Object> googleResponse = client.validate(endpoint, apiKey, buildRequest(reqVO));
        return mapResponse(reqVO, googleResponse);
    }

    @Override
    public AppAddressVerificationStatusRespVO.ProviderStatus getStatus() {
        AppAddressVerificationStatusRespVO.ProviderStatus status = new AppAddressVerificationStatusRespVO.ProviderStatus();
        status.setSource(SOURCE);
        status.setName("Google Address Validation");
        status.setEnabled(hasText(apiKey));
        status.setFallback(false);
        status.setReason(hasText(apiKey) ? "configured" : "missing-api-key");
        status.setUspsCassEnabled(enableUspsCass);
        return status;
    }

    private Map<String, Object> buildRequest(AppAddressVerifyReqVO reqVO) {
        AppAddressVerifyReqVO.Address sourceAddress = reqVO == null ? null : reqVO.getAddress();
        Map<String, Object> googleAddress = new HashMap<>();
        googleAddress.put("regionCode", normalizeCountry(sourceAddress));
        googleAddress.put("locality", clean(sourceAddress == null ? null : sourceAddress.getCity()));
        googleAddress.put("administrativeArea", clean(sourceAddress == null ? null : sourceAddress.getState()).toUpperCase());
        googleAddress.put("postalCode", clean(sourceAddress == null ? null : sourceAddress.getPostalCode()));
        googleAddress.put("addressLines", buildAddressLines(sourceAddress));

        Map<String, Object> request = new HashMap<>();
        request.put("address", googleAddress);
        request.put("enableUspsCass", enableUspsCass);
        return request;
    }

    private AppAddressVerifyRespVO mapResponse(AppAddressVerifyReqVO reqVO, Map<String, Object> googleResponse) {
        Map<String, Object> result = asMap(googleResponse == null ? null : googleResponse.get("result"));
        Map<String, Object> verdict = asMap(result.get("verdict"));
        Map<String, Object> googleAddress = asMap(result.get("address"));
        Map<String, Object> postalAddress = asMap(googleAddress.get("postalAddress"));

        boolean complete = Boolean.TRUE.equals(verdict.get("addressComplete"));
        boolean unconfirmed = Boolean.TRUE.equals(verdict.get("hasUnconfirmedComponents"));
        boolean inferred = Boolean.TRUE.equals(verdict.get("hasInferredComponents"));
        boolean replaced = Boolean.TRUE.equals(verdict.get("hasReplacedComponents"));
        boolean verified = complete && !unconfirmed;

        AppAddressVerifyRespVO respVO = new AppAddressVerifyRespVO();
        respVO.setSource(SOURCE);
        respVO.setStatus(verified ? "verified" : (postalAddress.isEmpty() ? "unverified" : "suggested"));
        respVO.setReason(verified ? "google-address-complete" : (postalAddress.isEmpty() ? "google-unverified" : "google-review-required"));
        respVO.setRequiresConfirmation(!complete || unconfirmed || inferred || replaced);
        respVO.setDeliverable(verified ? Boolean.TRUE : null);
        respVO.setOriginalAddress(getOriginalAddress(reqVO));
        respVO.setSuggestedAddress(buildSuggestedAddress(reqVO, postalAddress));
        respVO.setMetadata(buildMetadata(googleResponse, verdict, googleAddress));
        respVO.setProviderResponseId(asString(googleResponse == null ? null : googleResponse.get("responseId")));
        return respVO;
    }

    private static List<String> buildAddressLines(AppAddressVerifyReqVO.Address address) {
        List<String> addressLines = new ArrayList<>();
        if (address == null) {
            return addressLines;
        }
        if (hasText(address.getStreet())) {
            addressLines.add(clean(address.getStreet()));
        }
        if (hasText(address.getApartment())) {
            addressLines.add(clean(address.getApartment()));
        }
        return addressLines;
    }

    private static AppAddressVerifyReqVO.Address buildSuggestedAddress(AppAddressVerifyReqVO reqVO,
                                                                       Map<String, Object> postalAddress) {
        if (postalAddress.isEmpty()) {
            return null;
        }
        AppAddressVerifyReqVO.Address original = reqVO == null ? null : reqVO.getAddress();
        AppAddressVerifyReqVO.Address suggested = new AppAddressVerifyReqVO.Address();
        suggested.setFirstName(original == null ? "" : clean(original.getFirstName()));
        suggested.setLastName(original == null ? "" : clean(original.getLastName()));
        suggested.setStreet(firstAddressLine(postalAddress.get("addressLines")));
        suggested.setApartment(original == null ? "" : clean(original.getApartment()));
        suggested.setCity(clean(asString(postalAddress.get("locality"))));
        suggested.setState(clean(asString(postalAddress.get("administrativeArea"))));
        suggested.setPostalCode(clean(asString(postalAddress.get("postalCode"))));
        suggested.setPhone(original == null ? "" : clean(original.getPhone()));
        suggested.setCountry(countryName(asString(postalAddress.get("regionCode"))));
        return suggested;
    }

    private static Map<String, Object> buildMetadata(Map<String, Object> googleResponse, Map<String, Object> verdict,
                                                     Map<String, Object> googleAddress) {
        Map<String, Object> metadata = new HashMap<>();
        if (googleResponse != null && googleResponse.get("responseId") != null) {
            metadata.put("responseId", googleResponse.get("responseId"));
        }
        if (!verdict.isEmpty()) {
            metadata.put("verdict", verdict);
        }
        if (googleAddress.get("formattedAddress") != null) {
            metadata.put("formattedAddress", googleAddress.get("formattedAddress"));
        }
        return metadata;
    }

    private static Object getOriginalAddress(AppAddressVerifyReqVO reqVO) {
        if (reqVO != null && reqVO.getLocalVerification() != null
                && reqVO.getLocalVerification().get("originalAddress") != null) {
            return reqVO.getLocalVerification().get("originalAddress");
        }
        return reqVO == null ? null : reqVO.getAddress();
    }

    private static String firstAddressLine(Object addressLinesValue) {
        if (addressLinesValue instanceof List && !((List<?>) addressLinesValue).isEmpty()) {
            Object firstLine = ((List<?>) addressLinesValue).get(0);
            return firstLine == null ? "" : String.valueOf(firstLine);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    private static String normalizeCountry(AppAddressVerifyReqVO.Address address) {
        String country = address == null ? "" : clean(address.getCountry());
        return "US".equalsIgnoreCase(country) || "United States".equalsIgnoreCase(country) ? "US" : country;
    }

    private static String countryName(String regionCode) {
        return "US".equalsIgnoreCase(regionCode) ? "United States" : clean(regionCode);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

}
