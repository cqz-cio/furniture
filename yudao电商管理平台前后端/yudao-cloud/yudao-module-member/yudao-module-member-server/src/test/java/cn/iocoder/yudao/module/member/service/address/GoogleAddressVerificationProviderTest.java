package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GoogleAddressVerificationProviderTest {

    @Test
    public void testVerify_whenApiKeyMissing_returnsNull() {
        CapturingGoogleAddressValidationClient client = new CapturingGoogleAddressValidationClient(googleResponse(false));
        GoogleAddressVerificationProvider provider = new GoogleAddressVerificationProvider(
                "", "https://addressvalidation.googleapis.com/v1:validateAddress", true, client);

        AppAddressVerifyRespVO respVO = provider.verify(buildRequest());

        assertNull(respVO);
        assertFalse(client.called);
    }

    @Test
    public void testGetStatus_whenApiKeyMissing_reportsDisabledWithoutSecret() {
        GoogleAddressVerificationProvider provider = new GoogleAddressVerificationProvider(
                "", "https://addressvalidation.googleapis.com/v1:validateAddress", true,
                new CapturingGoogleAddressValidationClient(googleResponse(false)));

        AppAddressVerificationStatusRespVO.ProviderStatus status = provider.getStatus();

        assertEquals("google-address-validation", status.getSource());
        assertEquals("Google Address Validation", status.getName());
        assertFalse(status.getEnabled());
        assertFalse(status.getFallback());
        assertEquals("missing-api-key", status.getReason());
        assertEquals(Boolean.TRUE, status.getUspsCassEnabled());
        assertFalse(Arrays.stream(status.getClass().getDeclaredFields())
                .anyMatch(field -> "apiKey".equals(field.getName())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVerify_postsGoogleCassRequestAndMapsVerifiedResponse() {
        CapturingGoogleAddressValidationClient client = new CapturingGoogleAddressValidationClient(googleResponse(false));
        GoogleAddressVerificationProvider provider = new GoogleAddressVerificationProvider(
                "google-key", "https://addressvalidation.googleapis.com/v1:validateAddress", true, client);

        AppAddressVerifyRespVO respVO = provider.verify(buildRequest());

        assertEquals("https://addressvalidation.googleapis.com/v1:validateAddress", client.endpoint);
        assertEquals("google-key", client.apiKey);
        assertEquals(Boolean.TRUE, client.request.get("enableUspsCass"));
        Map<String, Object> googleAddress = (Map<String, Object>) client.request.get("address");
        assertEquals("US", googleAddress.get("regionCode"));
        assertEquals("Mountain View", googleAddress.get("locality"));
        assertEquals("CA", googleAddress.get("administrativeArea"));
        assertEquals("94043", googleAddress.get("postalCode"));
        assertEquals(Collections.singletonList("1600 Amphitheatre Parkway"), googleAddress.get("addressLines"));

        assertEquals("google-address-validation", respVO.getSource());
        assertEquals("verified", respVO.getStatus());
        assertEquals("google-address-complete", respVO.getReason());
        assertEquals(Boolean.FALSE, respVO.getRequiresConfirmation());
        assertEquals(Boolean.TRUE, respVO.getDeliverable());
        assertEquals("1600 AMPHITHEATRE PKWY", respVO.getSuggestedAddress().getStreet());
        assertEquals("Mountain View", respVO.getSuggestedAddress().getCity());
        assertEquals("CA", respVO.getSuggestedAddress().getState());
        assertEquals("94043-1351", respVO.getSuggestedAddress().getPostalCode());
        assertEquals("google-response-1", respVO.getProviderResponseId());
        assertEquals("google-response-1", respVO.getMetadata().get("responseId"));
    }

    @Test
    public void testVerify_whenGoogleHasUnconfirmedComponents_requiresUserConfirmation() {
        CapturingGoogleAddressValidationClient client = new CapturingGoogleAddressValidationClient(googleResponse(true));
        GoogleAddressVerificationProvider provider = new GoogleAddressVerificationProvider(
                "google-key", "https://addressvalidation.googleapis.com/v1:validateAddress", true, client);

        AppAddressVerifyRespVO respVO = provider.verify(buildRequest());

        assertEquals("suggested", respVO.getStatus());
        assertEquals("google-review-required", respVO.getReason());
        assertTrue(respVO.getRequiresConfirmation());
        assertNull(respVO.getDeliverable());
    }

    @Test
    public void testVerify_whenGoogleReturnsNullResponse_marksUnverifiedWithoutThrowing() {
        CapturingGoogleAddressValidationClient client = new CapturingGoogleAddressValidationClient(null);
        GoogleAddressVerificationProvider provider = new GoogleAddressVerificationProvider(
                "google-key", "https://addressvalidation.googleapis.com/v1:validateAddress", true, client);

        AppAddressVerifyRespVO respVO = provider.verify(buildRequest());

        assertEquals("google-address-validation", respVO.getSource());
        assertEquals("unverified", respVO.getStatus());
        assertEquals("google-unverified", respVO.getReason());
        assertTrue(respVO.getRequiresConfirmation());
        assertNull(respVO.getSuggestedAddress());
        assertEquals("", respVO.getProviderResponseId());
    }

    private static AppAddressVerifyReqVO buildRequest() {
        AppAddressVerifyReqVO.Address address = new AppAddressVerifyReqVO.Address();
        address.setFirstName("Ada");
        address.setLastName("Lovelace");
        address.setStreet("1600 Amphitheatre Parkway");
        address.setCity("Mountain View");
        address.setState("CA");
        address.setPostalCode("94043");
        address.setPhone("555-0100");
        address.setCountry("United States");

        Map<String, Object> originalAddress = new HashMap<>();
        originalAddress.put("street", "1600 Amphitheatre Parkway");
        originalAddress.put("city", "Mountain View");
        originalAddress.put("state", "CA");
        originalAddress.put("postalCode", "94043");
        Map<String, Object> localVerification = new HashMap<>();
        localVerification.put("originalAddress", originalAddress);

        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(address);
        reqVO.setLocalVerification(localVerification);
        return reqVO;
    }

    private static Map<String, Object> googleResponse(boolean hasUnconfirmedComponents) {
        Map<String, Object> postalAddress = new HashMap<>();
        postalAddress.put("addressLines", Collections.singletonList("1600 AMPHITHEATRE PKWY"));
        postalAddress.put("locality", "Mountain View");
        postalAddress.put("administrativeArea", "CA");
        postalAddress.put("postalCode", "94043-1351");
        postalAddress.put("regionCode", "US");

        Map<String, Object> address = new HashMap<>();
        address.put("formattedAddress", "1600 AMPHITHEATRE PKWY, MOUNTAIN VIEW, CA 94043-1351, USA");
        address.put("postalAddress", postalAddress);

        Map<String, Object> verdict = new HashMap<>();
        verdict.put("addressComplete", true);
        verdict.put("hasUnconfirmedComponents", hasUnconfirmedComponents);
        verdict.put("hasInferredComponents", false);
        verdict.put("hasReplacedComponents", false);

        Map<String, Object> result = new HashMap<>();
        result.put("verdict", verdict);
        result.put("address", address);

        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        response.put("responseId", "google-response-1");
        return response;
    }

    private static class CapturingGoogleAddressValidationClient implements GoogleAddressValidationClient {
        private final Map<String, Object> response;
        private boolean called;
        private String endpoint;
        private String apiKey;
        private Map<String, Object> request;

        private CapturingGoogleAddressValidationClient(Map<String, Object> response) {
            this.response = response;
        }

        @Override
        public Map<String, Object> validate(String endpoint, String apiKey, Map<String, Object> request) {
            this.called = true;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.request = request;
            return response;
        }
    }

}
