package cn.iocoder.yudao.module.trade.framework.fulfillment.core;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.ProviderTrackingEvent;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingQuery;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationResult;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingSnapshot;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.impl.MockLogisticsProviderClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_PROVIDER_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_PROVIDER_CAPABILITY_UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsProviderRegistryTest {

    @Test
    void getClient_normalizesProviderCodesWithLocaleRoot() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            LogisticsProviderClient client = provider("I");
            LogisticsProviderRegistry registry = new LogisticsProviderRegistry(List.of(client));

            assertSame(client, registry.getClient("i"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void constructor_rejectsDuplicateNormalizedProviderCodes() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new LogisticsProviderRegistry(List.of(provider("mock"), provider("MOCK"))));

        assertEquals("Duplicate logistics provider: mock", exception.getMessage());
    }

    @Test
    void getClient_throwsControlledErrorForUnknownProvider() {
        LogisticsProviderRegistry registry = new LogisticsProviderRegistry(List.of(provider("mock")));

        ServiceException exception = assertThrows(ServiceException.class, () -> registry.getClient("missing"));

        assertEquals(FULFILLMENT_PROVIDER_NOT_AVAILABLE.getCode(), exception.getCode());
    }

    @Test
    void mock_declaresTrackingQueryCapabilityOnly() {
        MockLogisticsProviderClient client = new MockLogisticsProviderClient(Map.of());

        assertEquals("mock", client.getProviderCode());
        assertEquals(Set.of(ProviderCapability.TRACKING_QUERY), client.getCapabilities());
    }

    @Test
    void mock_rejectsTrackingRegistrationWithControlledCapabilityError() {
        MockLogisticsProviderClient client = new MockLogisticsProviderClient(Map.of());
        TrackingRegistrationCommand command = new TrackingRegistrationCommand()
                .setCarrierCode("ups")
                .setTrackingNumber("1Z999");

        ServiceException exception = assertThrows(ServiceException.class, () -> client.registerTracking(command));

        assertEquals(FULFILLMENT_PROVIDER_CAPABILITY_UNSUPPORTED.getCode(), exception.getCode());
    }

    @Test
    void mock_returnsImmutableRawProviderEventsWithoutFixturePollution() {
        TrackingQuery query = new TrackingQuery()
                .setCarrierCode("ups")
                .setTrackingNumber("1Z999");
        List<ProviderTrackingEvent> events = new ArrayList<>(List.of(
                new ProviderTrackingEvent("I", LocalDateTime.of(2026, 7, 15, 9, 30),
                        "Los Angeles, CA", "Departed origin facility"),
                new ProviderTrackingEvent("OD", LocalDateTime.of(2026, 7, 16, 8, 0),
                        "Ontario, CA", "Out for delivery")));
        MockLogisticsProviderClient client = new MockLogisticsProviderClient(Map.of(query, events));
        events.clear();

        TrackingSnapshot first = client.queryTracking(query);
        assertThrows(UnsupportedOperationException.class, () -> first.getEvents().clear());
        TrackingSnapshot second = client.queryTracking(new TrackingQuery()
                .setCarrierCode("ups")
                .setTrackingNumber("1Z999"));

        assertEquals("ups", first.getCarrierCode());
        assertEquals("1Z999", first.getTrackingNumber());
        assertEquals(List.of("I", "OD"), first.getEvents().stream()
                .map(ProviderTrackingEvent::providerStatus)
                .toList());
        assertEquals(first, second);
        assertTrue(ProviderTrackingEvent.class.isRecord());
        assertFalse(referencesHttpClient(MockLogisticsProviderClient.class));
        assertFalse(hasControllerAnnotation(MockLogisticsProviderClient.class));
    }

    private static boolean referencesHttpClient(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getType().getName())
                .anyMatch(LogisticsProviderRegistryTest::isHttpClientType)
                || Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getName)
                .anyMatch(LogisticsProviderRegistryTest::isHttpClientType);
    }

    private static boolean isHttpClientType(String typeName) {
        return typeName.contains("RestTemplate") || typeName.contains("WebClient")
                || typeName.startsWith("java.net.http.");
    }

    private static boolean hasControllerAnnotation(AnnotatedElement type) {
        return Arrays.stream(type.getAnnotations())
                .map(annotation -> annotation.annotationType().getSimpleName())
                .anyMatch(name -> name.equals("Controller") || name.equals("RestController"));
    }

    private static LogisticsProviderClient provider(String code) {
        return new LogisticsProviderClient() {
            @Override
            public String getProviderCode() {
                return code;
            }

            @Override
            public Set<ProviderCapability> getCapabilities() {
                return Set.of();
            }

            @Override
            public TrackingRegistrationResult registerTracking(TrackingRegistrationCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TrackingSnapshot queryTracking(TrackingQuery query) {
                throw new UnsupportedOperationException();
            }
        };
    }

}
