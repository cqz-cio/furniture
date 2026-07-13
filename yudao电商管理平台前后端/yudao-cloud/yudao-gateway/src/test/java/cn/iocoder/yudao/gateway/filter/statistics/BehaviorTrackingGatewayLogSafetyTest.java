package cn.iocoder.yudao.gateway.filter.statistics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.gateway.filter.logging.AccessLogFilter;
import cn.iocoder.yudao.gateway.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BehaviorTrackingGatewayLogSafetyTest {

    private static final String SENTINEL = "SENSITIVE-VISITOR-SESSION-EVENT-203.0.113.9";

    @Test
    void trackingAccessAndExceptionLogs_neverContainSensitiveValues() {
        ListAppender<ILoggingEvent> accessAppender = appender(AccessLogFilter.class);
        MockServerWebExchange exchange = trackingExchange();
        new AccessLogFilter().filter(exchange, next -> {
            next.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return Mono.empty();
        }).block();
        assertNoSentinel(accessAppender);

        ListAppender<ILoggingEvent> exceptionAppender = appender(GlobalExceptionHandler.class);
        MockServerWebExchange failed = trackingExchange();
        new GlobalExceptionHandler().handle(failed,
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, SENTINEL)).block();
        assertNoSentinel(exceptionAppender);
        assertFalse(failed.getResponse().getBodyAsString().block().contains(SENTINEL));
    }

    private MockServerWebExchange trackingExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.post(BehaviorTrackingGatewayFilter.TRACK_PATH)
                .header("x-analytics-visitor-id", SENTINEL)
                .header("x-analytics-session-id", SENTINEL)
                .header("x-analytics-consent-evidence", SENTINEL)
                .header("user-agent", SENTINEL)
                .body("{\"eventId\":\"" + SENTINEL + "\"}"));
    }

    private ListAppender<ILoggingEvent> appender(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void assertNoSentinel(ListAppender<ILoggingEvent> appender) {
        String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(messages.contains(SENTINEL), messages);
    }
}
