package cn.iocoder.yudao.gateway.filter.statistics;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class BehaviorTrackingGatewayFilter implements GlobalFilter, Ordered {

    public static final String TRACK_PATH = "/app-api/statistics/behavior/track";
    public static final String CONSENT_EVIDENCE_PATH = "/app-api/statistics/consent/evidence";
    public static final String CONSENT_WITHDRAW_PATH = "/app-api/statistics/consent/withdraw";
    private static final Set<String> PUBLIC_ANALYTICS_PATHS = Set.of(
            TRACK_PATH, CONSENT_EVIDENCE_PATH, CONSENT_WITHDRAW_PATH);
    private static final Set<String> UNTRUSTED_IDENTITY_HEADERS = new HashSet<>(Arrays.asList(
            "tenant-id", "login-user", "x-user-id", "x-user-type", "x-tenant-id",
            "x-internal-user-id", "x-internal-tenant-id", "authorization", "cookie",
            "x-vanz-inquiry-secret"));

    private final BehaviorTrackingGatewayProperties properties;

    public BehaviorTrackingGatewayFilter(BehaviorTrackingGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!PUBLIC_ANALYTICS_PATHS.contains(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        if (!properties.isEnabled()) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        HttpMethod method = exchange.getRequest().getMethod();
        if (method != HttpMethod.POST && method != HttpMethod.OPTIONS) {
            exchange.getResponse().getHeaders().set(HttpHeaders.ALLOW, "POST, OPTIONS");
            return reject(exchange, HttpStatus.METHOD_NOT_ALLOWED);
        }
        String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
        String origin = exchange.getRequest().getHeaders().getOrigin();
        BehaviorTrackingGatewayProperties.AllowedSite site = properties.getAllowedSites().stream()
                .filter(candidate -> candidate.getHost() != null && candidate.getHost().equals(host)
                        && candidate.getOrigin() != null && candidate.getOrigin().equals(origin)
                        && candidate.getTenantId() != null)
                .findFirst().orElse(null);
        if (site == null) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, site.getOrigin());
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST, OPTIONS");
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                "Content-Type, X-Analytics-Visitor-Id, X-Analytics-Session-Id, X-Analytics-Consent-Evidence");
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            UNTRUSTED_IDENTITY_HEADERS.forEach(headers::remove);
            headers.set("tenant-id", String.valueOf(site.getTenantId()));
        }).build();
        if (method == HttpMethod.OPTIONS) {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
