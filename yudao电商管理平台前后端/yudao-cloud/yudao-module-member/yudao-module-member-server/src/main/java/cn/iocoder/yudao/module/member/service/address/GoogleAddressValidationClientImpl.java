package cn.iocoder.yudao.module.member.service.address;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

@Component
public class GoogleAddressValidationClientImpl implements GoogleAddressValidationClient {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

    private final RestTemplate restTemplate;

    public GoogleAddressValidationClientImpl(
            @Value("${yudao.member.address-verification.google.connect-timeout-millis:3000}") int connectTimeoutMillis,
            @Value("${yudao.member.address-verification.google.read-timeout-millis:5000}") int readTimeoutMillis) {
        this(new RestTemplate(buildRequestFactory(connectTimeoutMillis, readTimeoutMillis)));
    }

    GoogleAddressValidationClientImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> validate(String endpoint, String apiKey, Map<String, Object> request) {
        return restTemplate.postForObject(buildUrl(endpoint, apiKey), request, Map.class);
    }

    private static String buildUrl(String endpoint, String apiKey) {
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator + "key=" + encode(apiKey);
    }

    static SimpleClientHttpRequestFactory buildRequestFactory(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(normalizeTimeoutMillis(connectTimeoutMillis, DEFAULT_CONNECT_TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(normalizeTimeoutMillis(readTimeoutMillis, DEFAULT_READ_TIMEOUT_MILLIS));
        return requestFactory;
    }

    private static int normalizeTimeoutMillis(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not available", e);
        }
    }

}
