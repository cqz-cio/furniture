package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerifyRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressVerificationStatusRespVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddressVerificationServiceImplTest {

    private final AddressVerificationServiceImpl addressVerificationService = new AddressVerificationServiceImpl();

    @Test
    public void testVerifyAddress_standardizesAddressWithoutClaimingDeliverability() {
        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(buildAddress("Ada", "Lovelace", "1600 amphitheatre parkway",
                "Mountain View", "ca", "94043-1351", "555-0100"));
        reqVO.setLocalVerification(buildLocalVerification());

        AppAddressVerifyRespVO respVO = addressVerificationService.verifyAddress(reqVO);

        assertEquals("backend-address-verification", respVO.getSource());
        assertEquals("suggested", respVO.getStatus());
        assertEquals("backend-standardized", respVO.getReason());
        assertTrue(respVO.getRequiresConfirmation());
        assertNull(respVO.getDeliverable());
        assertEquals("1600 AMPHITHEATRE PKWY", respVO.getSuggestedAddress().getStreet());
        assertEquals("CA", respVO.getSuggestedAddress().getState());
        assertEquals("94043", respVO.getSuggestedAddress().getPostalCode());
        assertEquals(reqVO.getLocalVerification().get("originalAddress"), respVO.getOriginalAddress());
    }

    @Test
    public void testVerifyAddress_marksIncompleteAddressUnverified() {
        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(buildAddress("Ada", "", "", "Mountain View", "CA", "94043", "555-0100"));

        AppAddressVerifyRespVO respVO = addressVerificationService.verifyAddress(reqVO);

        assertEquals("unverified", respVO.getStatus());
        assertEquals("missing-required-fields", respVO.getReason());
        assertTrue(respVO.getRequiresConfirmation());
        assertNull(respVO.getSuggestedAddress());
    }

    @Test
    public void testVerifyAddress_usesExternalProviderBeforeLocalFallback() {
        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(buildAddress("Ada", "Lovelace", "1 main street",
                "New York", "NY", "10001", "555-0100"));
        AddressVerificationServiceImpl service = new AddressVerificationServiceImpl(Collections.singletonList(new AddressVerificationProvider() {
            @Override
            public AppAddressVerifyRespVO verify(AppAddressVerifyReqVO request) {
                AppAddressVerifyRespVO respVO = new AppAddressVerifyRespVO();
                respVO.setSource("cass-provider");
                respVO.setStatus("verified");
                respVO.setReason("cass-deliverable");
                respVO.setRequiresConfirmation(false);
                respVO.setDeliverable(true);
                return respVO;
            }
        }));

        AppAddressVerifyRespVO respVO = service.verifyAddress(reqVO);

        assertEquals("cass-provider", respVO.getSource());
        assertEquals("verified", respVO.getStatus());
        assertEquals("cass-deliverable", respVO.getReason());
        assertEquals(Boolean.TRUE, respVO.getDeliverable());
    }

    @Test
    public void testVerifyAddress_sortsConfiguredProvidersBeforeVerification() {
        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(buildAddress("Ada", "Lovelace", "1600 Amphitheatre Parkway",
                "Mountain View", "CA", "94043", "555-0100"));
        AddressVerificationServiceImpl service = new AddressVerificationServiceImpl(Arrays.asList(
                new LocalAddressVerificationProvider(),
                new GoogleAddressVerificationProvider("google-key",
                        "https://addressvalidation.googleapis.com/v1:validateAddress", true,
                        (endpoint, apiKey, request) -> googleResponse())
        ));

        AppAddressVerifyRespVO respVO = service.verifyAddress(reqVO);

        assertEquals("google-address-validation", respVO.getSource());
        assertEquals("verified", respVO.getStatus());
        assertEquals("google-address-complete", respVO.getReason());
        assertEquals(Boolean.TRUE, respVO.getDeliverable());
    }

    @Test
    public void testVerifyAddress_fallsBackWhenExternalProviderFails() {
        AppAddressVerifyReqVO reqVO = new AppAddressVerifyReqVO();
        reqVO.setAddress(buildAddress("Ada", "Lovelace", "1 main street",
                "New York", "ny", "10001-0001", "555-0100"));
        AddressVerificationServiceImpl service = new AddressVerificationServiceImpl(Arrays.asList(
                request -> {
                    throw new IllegalStateException("provider unavailable");
                },
                new LocalAddressVerificationProvider()
        ));

        AppAddressVerifyRespVO respVO = service.verifyAddress(reqVO);

        assertEquals("backend-address-verification", respVO.getSource());
        assertEquals("suggested", respVO.getStatus());
        assertEquals("backend-standardized", respVO.getReason());
        assertEquals("1 MAIN ST", respVO.getSuggestedAddress().getStreet());
        assertEquals("NY", respVO.getSuggestedAddress().getState());
        assertEquals("fallback", respVO.getProviderStatus());
    }

    @Test
    public void testGetStatus_reportsConfiguredProviderChainAndFallbackRisk() {
        AddressVerificationServiceImpl service = new AddressVerificationServiceImpl(Arrays.asList(
                new GoogleAddressVerificationProvider("", "https://addressvalidation.googleapis.com/v1:validateAddress",
                        true, (endpoint, apiKey, request) -> Collections.emptyMap()),
                new LocalAddressVerificationProvider()
        ));

        AppAddressVerificationStatusRespVO status = service.getStatus();

        assertEquals("fallback", status.getMode());
        assertTrue(status.getFallbackActive());
        assertEquals(2, status.getProviders().size());
        assertEquals("google-address-validation", status.getProviders().get(0).getSource());
        assertEquals(Boolean.FALSE, status.getProviders().get(0).getEnabled());
        assertEquals("missing-api-key", status.getProviders().get(0).getReason());
        assertEquals("backend-address-verification", status.getProviders().get(1).getSource());
        assertEquals(Boolean.TRUE, status.getProviders().get(1).getEnabled());
        assertEquals(Boolean.TRUE, status.getProviders().get(1).getFallback());
    }

    private static AppAddressVerifyReqVO.Address buildAddress(String firstName, String lastName, String street,
                                                              String city, String state, String postalCode, String phone) {
        AppAddressVerifyReqVO.Address address = new AppAddressVerifyReqVO.Address();
        address.setFirstName(firstName);
        address.setLastName(lastName);
        address.setStreet(street);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postalCode);
        address.setPhone(phone);
        address.setCountry("United States");
        return address;
    }

    private static Map<String, Object> buildLocalVerification() {
        Map<String, Object> originalAddress = new HashMap<>();
        originalAddress.put("street", "1600 AMPHITHEATRE PKWY");
        originalAddress.put("city", "Mountain View");
        originalAddress.put("state", "CA");
        originalAddress.put("postalCode", "94043");

        Map<String, Object> localVerification = new HashMap<>();
        localVerification.put("originalAddress", originalAddress);
        return localVerification;
    }

    private static Map<String, Object> googleResponse() {
        Map<String, Object> postalAddress = new HashMap<>();
        postalAddress.put("addressLines", Collections.singletonList("1600 AMPHITHEATRE PKWY"));
        postalAddress.put("locality", "Mountain View");
        postalAddress.put("administrativeArea", "CA");
        postalAddress.put("postalCode", "94043-1351");
        postalAddress.put("regionCode", "US");

        Map<String, Object> address = new HashMap<>();
        address.put("postalAddress", postalAddress);

        Map<String, Object> verdict = new HashMap<>();
        verdict.put("addressComplete", true);
        verdict.put("hasUnconfirmedComponents", false);
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

}
