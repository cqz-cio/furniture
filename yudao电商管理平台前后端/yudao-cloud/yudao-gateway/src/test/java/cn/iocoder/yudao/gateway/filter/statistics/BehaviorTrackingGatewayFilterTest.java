package cn.iocoder.yudao.gateway.filter.statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BehaviorTrackingGatewayFilterTest {

    private BehaviorTrackingGatewayFilter filter;

    @BeforeEach
    void setUp() {
        BehaviorTrackingGatewayProperties properties = new BehaviorTrackingGatewayProperties();
        properties.setEnabled(true);
        BehaviorTrackingGatewayProperties.AllowedSite site = new BehaviorTrackingGatewayProperties.AllowedSite();
        site.setHost("shop.oakved.example");
        site.setOrigin("https://shop.oakved.example");
        site.setTenantId(121L);
        properties.setAllowedSites(Collections.singletonList(site));
        filter = new BehaviorTrackingGatewayFilter(properties);
    }

    @Test
    void allowedPair_replacesSpoofedIdentityHeaders() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "shop.oakved.example",
                "https://shop.oakved.example", builder -> builder
                        .header("tenant-id", "999").header("login-user", "forged")
                        .header("x-user-id", "7").header("authorization", "Bearer forged")
                        .header("cookie", "admin=forged")
                        .header("x-analytics-visitor-id", "visitor"));
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> { forwarded.set(next.getRequest().getHeaders()); return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertEquals("121", forwarded.get().getFirst("tenant-id"));
        assertFalse(forwarded.get().containsKey("login-user"));
        assertFalse(forwarded.get().containsKey("x-user-id"));
        assertFalse(forwarded.get().containsKey("authorization"));
        assertFalse(forwarded.get().containsKey("cookie"));
        assertEquals("visitor", forwarded.get().getFirst("x-analytics-visitor-id"));
        assertEquals("https://shop.oakved.example", exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void consentEndpoints_areClaimedAndPreflightStopsBeforeDownstream() {
        for (String path : new String[]{
                BehaviorTrackingGatewayFilter.CONSENT_EVIDENCE_PATH,
                BehaviorTrackingGatewayFilter.CONSENT_WITHDRAW_PATH}) {
            MockServerWebExchange exchange = exchange(HttpMethod.POST, path,
                    "shop.oakved.example", "https://shop.oakved.example", builder -> builder);
            AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
            filter.filter(exchange, next -> {
                forwarded.set(next.getRequest().getHeaders());
                return Mono.empty();
            }).block();
            assertNotNull(forwarded.get());
            assertEquals("121", forwarded.get().getFirst("tenant-id"));
        }

        MockServerWebExchange preflight = exchange(HttpMethod.OPTIONS,
                BehaviorTrackingGatewayFilter.CONSENT_EVIDENCE_PATH,
                "shop.oakved.example", "https://shop.oakved.example", builder -> builder);
        filter.filter(preflight, next -> failChain()).block();
        assertEquals(HttpStatus.NO_CONTENT, preflight.getResponse().getStatusCode());
    }

    @Test
    void nonPostAnalyticsMethod_isRejected() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET,
                BehaviorTrackingGatewayFilter.TRACK_PATH,
                "shop.oakved.example", "https://shop.oakved.example", builder -> builder);
        filter.filter(exchange, next -> failChain()).block();
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exchange.getResponse().getStatusCode());
        assertEquals("POST, OPTIONS", exchange.getResponse().getHeaders().getFirst(HttpHeaders.ALLOW));
    }

    @Test
    void wrongHost_isForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "evil.example",
                "https://shop.oakved.example", builder -> builder);
        filter.filter(exchange, next -> failChain()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void wrongOrNullOrigin_isForbidden() {
        MockServerWebExchange wrong = exchange(HttpMethod.POST, "shop.oakved.example", "https://evil.example", b -> b);
        filter.filter(wrong, next -> failChain()).block();
        assertEquals(HttpStatus.FORBIDDEN, wrong.getResponse().getStatusCode());
        MockServerWebExchange missing = exchange(HttpMethod.POST, "shop.oakved.example", null, b -> b);
        filter.filter(missing, next -> failChain()).block();
        assertEquals(HttpStatus.FORBIDDEN, missing.getResponse().getStatusCode());
    }

    @Test
    void nonTrackingPath_isNotClaimedByFilter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/app-api/statistics/other").build());
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        filter.filter(exchange, next -> { called.set(true); return Mono.empty(); }).block();
        assertTrue(called.get());
    }

    private MockServerWebExchange exchange(HttpMethod method, String host, String origin,
                                            java.util.function.UnaryOperator<MockServerHttpRequest.BaseBuilder<?>> customizer) {
        return exchange(method, BehaviorTrackingGatewayFilter.TRACK_PATH, host, origin, customizer);
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String host, String origin,
                                            java.util.function.UnaryOperator<MockServerHttpRequest.BaseBuilder<?>> customizer) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path)
                .header(HttpHeaders.HOST, host);
        if (origin != null) builder.header(HttpHeaders.ORIGIN, origin);
        return MockServerWebExchange.from(customizer.apply(builder).build());
    }

    private Mono<Void> failChain() {
        fail("request must not reach downstream");
        return Mono.empty();
    }
}
