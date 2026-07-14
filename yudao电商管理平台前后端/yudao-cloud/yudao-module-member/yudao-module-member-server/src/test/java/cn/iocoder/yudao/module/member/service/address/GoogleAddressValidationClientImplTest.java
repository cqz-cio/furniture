package cn.iocoder.yudao.module.member.service.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleAddressValidationClientImplTest {

    @Test
    public void testBuildRequestFactory_configuresTimeouts() {
        SimpleClientHttpRequestFactory requestFactory =
                GoogleAddressValidationClientImpl.buildRequestFactory(2500, 6000);

        assertEquals(2500, getTimeout(requestFactory, "connectTimeout"));
        assertEquals(6000, getTimeout(requestFactory, "readTimeout"));
    }

    @Test
    public void testBuildRequestFactory_normalizesInvalidTimeouts() {
        SimpleClientHttpRequestFactory requestFactory =
                GoogleAddressValidationClientImpl.buildRequestFactory(0, -1);

        assertEquals(3000, getTimeout(requestFactory, "connectTimeout"));
        assertEquals(5000, getTimeout(requestFactory, "readTimeout"));
    }

    private static Integer getTimeout(SimpleClientHttpRequestFactory requestFactory, String fieldName) {
        return (Integer) ReflectionTestUtils.getField(requestFactory, fieldName);
    }

}
