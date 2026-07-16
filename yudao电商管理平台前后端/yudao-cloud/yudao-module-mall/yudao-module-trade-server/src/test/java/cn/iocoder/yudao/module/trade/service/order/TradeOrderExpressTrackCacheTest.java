package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackQueryReqDTO;
import cn.iocoder.yudao.module.trade.framework.fulfillment.cache.ExpressTrackCacheKeyGenerator;
import cn.iocoder.yudao.module.trade.framework.fulfillment.cache.ExpressTrackCachePolicy;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeOrderExpressTrackCacheTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void blankSecretBypassesCacheWhileConfiguredSecretUsesTenantAwareCache() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CacheTestConfig.class)) {
            TradeOrderQueryServiceImpl service = context.getBean(TradeOrderQueryServiceImpl.class);
            FulfillmentProperties properties = context.getBean(FulfillmentProperties.class);
            ExpressClient client = context.getBean(ExpressClient.class);
            TenantContextHolder.setTenantId(121L);

            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            verify(client, org.mockito.Mockito.times(2)).getExpressTrackList(any(ExpressTrackQueryReqDTO.class));

            properties.setIdempotencyHmacKey("test-only-fulfillment-cache-key-32-bytes");
            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            verify(client, org.mockito.Mockito.times(3)).getExpressTrackList(any(ExpressTrackQueryReqDTO.class));

            TenantContextHolder.setTenantId(122L);
            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            verify(client, org.mockito.Mockito.times(4)).getExpressTrackList(any(ExpressTrackQueryReqDTO.class));

            properties.setIdempotencyHmacKey("different-test-only-fulfillment-cache-key");
            service.getExpressTrackList("ups", "1Z-PRIVATE-123", "+1-416-555-0199");
            verify(client, org.mockito.Mockito.times(5)).getExpressTrackList(any(ExpressTrackQueryReqDTO.class));

            CacheManager cacheManager = context.getBean(CacheManager.class);
            assertEquals(3, ((java.util.concurrent.ConcurrentMap<?, ?>) cacheManager
                    .getCache("express_track").getNativeCache()).size());
        }
    }

    @Test
    void projectionFallbackCallsLegacyProviderExactlyOnce() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CacheTestConfig.class)) {
            TradeOrderQueryServiceImpl service = context.getBean(TradeOrderQueryServiceImpl.class);
            FulfillmentProperties properties = context.getBean(FulfillmentProperties.class);
            TradeOrderMapper orderMapper = context.getBean(TradeOrderMapper.class);
            DeliveryExpressService expressService = context.getBean(DeliveryExpressService.class);
            FulfillmentLegacyProjectionService projectionService = context.getBean(
                    FulfillmentLegacyProjectionService.class);
            ExpressClient client = context.getBean(ExpressClient.class);
            TenantContextHolder.setTenantId(121L);
            properties.setReadFromNewModel(true);
            properties.setIdempotencyHmacKey("test-only-fulfillment-cache-key-32-bytes");
            TradeOrderDO order = new TradeOrderDO().setId(501L).setUserId(601L).setLogisticsId(91L)
                    .setLogisticsNo("1Z-PRIVATE-123").setReceiverMobile("+1-416-555-0199");
            when(orderMapper.selectByIdAndUserId(501L, 601L)).thenReturn(order);
            when(projectionService.project(121L, 501L)).thenReturn(FulfillmentLegacyProjectionResult.fallback());
            when(expressService.getDeliveryExpress(91L)).thenReturn(new DeliveryExpressDO().setCode("ups"));

            assertEquals(List.of(), service.getExpressTrackList(501L, 601L));

            verify(projectionService).project(121L, 501L);
            verify(client).getExpressTrackList(any(ExpressTrackQueryReqDTO.class));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    static class CacheTestConfig {

        @Bean
        TradeOrderQueryServiceImpl tradeOrderQueryService() {
            return new TradeOrderQueryServiceImpl();
        }

        @Bean
        SpringUtil springUtil() {
            return new SpringUtil();
        }

        @Bean
        FulfillmentProperties fulfillmentProperties() {
            return new FulfillmentProperties();
        }

        @Bean
        ExpressTrackCachePolicy expressTrackCachePolicy(FulfillmentProperties properties) {
            return new ExpressTrackCachePolicy(properties);
        }

        @Bean
        ExpressTrackCacheKeyGenerator expressTrackCacheKeyGenerator(FulfillmentProperties properties) {
            return new ExpressTrackCacheKeyGenerator(properties);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        ExpressClient expressClient() {
            ExpressClient client = mock(ExpressClient.class);
            when(client.getExpressTrackList(any(ExpressTrackQueryReqDTO.class))).thenReturn(List.of());
            return client;
        }

        @Bean
        ExpressClientFactory expressClientFactory(ExpressClient client) {
            ExpressClientFactory factory = mock(ExpressClientFactory.class);
            when(factory.getDefaultExpressClient()).thenReturn(client);
            return factory;
        }

        @Bean
        TradeOrderMapper tradeOrderMapper() {
            return mock(TradeOrderMapper.class);
        }

        @Bean
        TradeOrderItemMapper tradeOrderItemMapper() {
            return mock(TradeOrderItemMapper.class);
        }

        @Bean
        DeliveryExpressService deliveryExpressService() {
            return mock(DeliveryExpressService.class);
        }

        @Bean
        MemberUserApi memberUserApi() {
            return mock(MemberUserApi.class);
        }

        @Bean
        FulfillmentLegacyProjectionService fulfillmentLegacyProjectionService() {
            return mock(FulfillmentLegacyProjectionService.class);
        }
    }

}
