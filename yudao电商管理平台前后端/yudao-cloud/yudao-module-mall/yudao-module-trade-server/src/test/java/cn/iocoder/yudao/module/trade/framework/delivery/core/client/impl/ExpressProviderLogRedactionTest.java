package cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl.kd100.Kd100ExpressClient;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.impl.kdniao.KdNiaoExpressClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_ERROR;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.EXPRESS_API_QUERY_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpressProviderLogRedactionTest {

    private static final String TRACKING = "TRACKING_CANARY_1Z999";
    private static final String PHONE = "PHONE_CANARY_14165550199";
    private static final String SECRET = "SIGNING_SECRET_CANARY";
    private static final String ACCOUNT = "ACCOUNT_CANARY";
    private static final String RESPONSE = "RESPONSE_BODY_CANARY";

    @Test
    void kd100DebugLogsContainOnlySafeMetadata() {
        RestTemplate restTemplate = restTemplate(
                "{\"result\":\"true\",\"nu\":\"" + RESPONSE + "\",\"data\":[]}");
        TradeExpressProperties.Kd100Config config = new TradeExpressProperties.Kd100Config()
                .setCustomer(ACCOUNT).setKey(SECRET);
        Kd100ExpressClient client = new Kd100ExpressClient(restTemplate, config);

        List<String> logs = capture(Kd100ExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(logs);
        assertTrue(logs.stream().anyMatch(message -> message.contains("provider=kd100")));
    }

    @Test
    void kdNiaoDebugLogsContainOnlySafeMetadata() {
        RestTemplate restTemplate = restTemplate(
                "{\"Success\":true,\"LogisticCode\":\"" + RESPONSE + "\","
                        + "\"EBusinessID\":\"" + ACCOUNT + "\",\"Traces\":[]}");
        TradeExpressProperties.KdNiaoConfig config = new TradeExpressProperties.KdNiaoConfig()
                .setBusinessId(ACCOUNT).setApiKey(SECRET).setRequestType("1002");
        KdNiaoExpressClient client = new KdNiaoExpressClient(restTemplate, config);

        List<String> logs = capture(KdNiaoExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(logs);
        assertTrue(logs.stream().anyMatch(message -> message.contains("provider=kdniao")));
    }

    @Test
    void kd100ProviderFailureDoesNotExposeProviderMessage() {
        Kd100ExpressClient client = new Kd100ExpressClient(restTemplate(
                "{\"result\":\"false\",\"message\":\"" + RESPONSE + "\"}"),
                new TradeExpressProperties.Kd100Config().setCustomer(ACCOUNT).setKey(SECRET));

        FailureCapture failure = captureFailure(Kd100ExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_FAILED.getCode());
    }

    @Test
    void kdNiaoProviderFailureDoesNotExposeProviderReason() {
        KdNiaoExpressClient client = new KdNiaoExpressClient(restTemplate(
                "{\"Success\":false,\"Reason\":\"" + RESPONSE + "\"}"),
                new TradeExpressProperties.KdNiaoConfig().setBusinessId(ACCOUNT)
                        .setApiKey(SECRET).setRequestType("1002"));

        FailureCapture failure = captureFailure(KdNiaoExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_FAILED.getCode());
    }

    @Test
    void kd100Non2xxDoesNotExposeResponseBody() {
        Kd100ExpressClient client = new Kd100ExpressClient(restTemplate(RESPONSE, HttpStatus.BAD_GATEWAY),
                new TradeExpressProperties.Kd100Config().setCustomer(ACCOUNT).setKey(SECRET));

        FailureCapture failure = captureFailure(Kd100ExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_ERROR.getCode());
    }

    @Test
    void kdNiaoNon2xxDoesNotExposeResponseBody() {
        KdNiaoExpressClient client = new KdNiaoExpressClient(restTemplate(RESPONSE, HttpStatus.BAD_GATEWAY),
                new TradeExpressProperties.KdNiaoConfig().setBusinessId(ACCOUNT)
                        .setApiKey(SECRET).setRequestType("1002"));

        FailureCapture failure = captureFailure(KdNiaoExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_ERROR.getCode());
    }

    @Test
    void transportFailureDoesNotExposeCause() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException(RESPONSE));
        Kd100ExpressClient client = new Kd100ExpressClient(restTemplate,
                new TradeExpressProperties.Kd100Config().setCustomer(ACCOUNT).setKey(SECRET));

        FailureCapture failure = captureFailure(Kd100ExpressClient.class,
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_ERROR.getCode());
    }

    @Test
    void parseFailureDoesNotExposeResponseBodyToGlobalLogs() {
        KdNiaoExpressClient client = new KdNiaoExpressClient(restTemplate("{\"Success\":" + RESPONSE),
                new TradeExpressProperties.KdNiaoConfig().setBusinessId(ACCOUNT)
                        .setApiKey(SECRET).setRequestType("1002"));

        FailureCapture failure = captureRootFailure(
                () -> client.getExpressTrackList(query()));

        assertSafe(failure.logs());
        assertSafeFailure(failure.exception(), EXPRESS_API_QUERY_ERROR.getCode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RestTemplate restTemplate(String responseBody) {
        return restTemplate(responseBody, HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RestTemplate restTemplate(String responseBody, HttpStatus status) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity(responseBody, status));
        return restTemplate;
    }

    private static ExpressTrackQueryReqDTO query() {
        return new ExpressTrackQueryReqDTO().setExpressCode("sf").setLogisticsNo(TRACKING).setPhone(PHONE);
    }

    private static List<String> capture(Class<?> loggerType, Runnable action) {
        return capture(LoggerFactory.getLogger(loggerType), action);
    }

    private static List<String> capture(org.slf4j.Logger slf4jLogger, Runnable action) {
        Logger logger = (Logger) slf4jLogger;
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    private static FailureCapture captureFailure(Class<?> loggerType, Runnable action) {
        AtomicReference<ServiceException> exception = new AtomicReference<>();
        List<String> logs = capture(loggerType,
                () -> exception.set(assertThrows(ServiceException.class, action::run)));
        return new FailureCapture(exception.get(), logs);
    }

    private static FailureCapture captureRootFailure(Runnable action) {
        AtomicReference<ServiceException> exception = new AtomicReference<>();
        List<String> logs = capture(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME),
                () -> exception.set(assertThrows(ServiceException.class, action::run)));
        return new FailureCapture(exception.get(), logs);
    }

    private static void assertSafeFailure(ServiceException exception, int expectedCode) {
        assertEquals(expectedCode, exception.getCode());
        assertNull(exception.getCause());
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        String rendered = exception + "\n" + stackTrace;
        assertFalse(rendered.contains(RESPONSE));
        assertFalse(rendered.contains(TRACKING));
        assertFalse(rendered.contains(PHONE));
        assertFalse(rendered.contains(SECRET));
        assertFalse(rendered.contains(ACCOUNT));
    }

    private static void assertSafe(List<String> logs) {
        String joined = String.join("\n", logs);
        assertFalse(joined.contains(TRACKING));
        assertFalse(joined.contains(PHONE));
        assertFalse(joined.contains(SECRET));
        assertFalse(joined.contains(ACCOUNT));
        assertFalse(joined.contains(RESPONSE));
        assertFalse(joined.contains("requestBody"));
        assertFalse(joined.contains("responseEntity"));
        assertFalse(joined.contains("DataSign"));
    }

    private record FailureCapture(ServiceException exception, List<String> logs) {
    }

}
