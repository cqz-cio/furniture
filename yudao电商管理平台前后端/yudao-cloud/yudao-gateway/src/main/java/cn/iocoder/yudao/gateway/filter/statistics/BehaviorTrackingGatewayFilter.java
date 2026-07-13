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
    private static final Set<String> UNTRUSTED_IDENTITY_HEADERS = new HashSet<>(Arrays.asList(
            "tenant-id", "login-user", "x-user-id", "x-user-type", "x-tenant-id",
            "x-internal-user-id", "x-internal-tenant-id"));

    private final BehaviorTrackingGatewayProperties properties;

    public BehaviorTrackingGatewayFilter(BehaviorTrackingGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!TRACK_PATH.equals(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        if (!properties.isEnabled()) {
            return reject(exchange, HttpStatus.NOT_FOUND);
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
                "Content-Type, Authorization, X-Analytics-Visitor-Id, X-Analytics-Session-Id, X-Analytics-Consent");
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            UNTRUSTED_IDENTITY_HEADERS.forEach(headers::remove);
            headers.set("tenant-id", String.valueOf(site.getTenantId()));
        }).build();
        if (request.getMethod() == HttpMethod.OPTIONS) {
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
