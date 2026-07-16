package cn.iocoder.yudao.module.trade.job.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJobAspect;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.FulfillmentLegacyMigrationService;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationBatchResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationOrderResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(FulfillmentLegacyMigrationJobAopIntegrationTest.TestConfig.class)
class FulfillmentLegacyMigrationJobAopIntegrationTest {

    private static final Long TENANT_ID = 121L;
    private static final String TRACKING_CANARY = "1Z999AA10123456784";
    private static final String PHONE_CANARY = "+1-202-555-0198";

    @Autowired
    private FulfillmentLegacyMigrationJob job;
    @Autowired
    private FulfillmentLegacyMigrationService migrationService;
    @Autowired
    private TenantFrameworkService tenantFrameworkService;
    @Autowired
    private FulfillmentFeatureGuard featureGuard;

    private Path logFile;

    @BeforeEach
    void setUp() {
        reset(migrationService, tenantFrameworkService, featureGuard);
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(TENANT_ID));
    }

    @AfterEach
    void tearDown() throws Exception {
        XxlJobContext.setXxlJobContext(null);
        TenantContextHolder.clear();
        if (logFile != null) {
            Files.deleteIfExists(logFile);
        }
    }

    @Test
    void springAopShouldPublishOnlySafeSuccessSummaryToXxlContext() {
        Path logFile = installXxlContext();
        MigrationBatchResult result = new MigrationBatchResult(true, 1, 1, 0, 0, 0,
                987654321L, false, List.of(
                new MigrationOrderResult(111111L, MigrationOutcome.WOULD_MIGRATE,
                        TRACKING_CANARY + PHONE_CANARY)));
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true)).thenAnswer(invocation -> {
            assertEquals(TENANT_ID, TenantContextHolder.getRequiredTenantId());
            return result;
        });

        String proxyReturnValue = job.execute("");

        assertTrue(AopUtils.isAopProxy(job));
        assertNull(proxyReturnValue);
        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, currentXxlContext().getHandleCode());
        String output = currentXxlContext().getHandleMsg();
        assertTrue(output.contains("WOULD_MIGRATE"));
        assertFalse(output.contains(TRACKING_CANARY));
        assertFalse(output.contains(PHONE_CANARY));
        assertFalse(output.contains("111111"));
        assertFalse(Files.exists(logFile));
        assertNull(TenantContextHolder.getTenantId());
        verify(migrationService).migrateActiveOrders(TENANT_ID, 0L, 100, true);
        verifyNoInteractions(featureGuard);
    }

    @Test
    void springAopShouldKeepFailureOutputAndXxlLogFreeOfSensitiveCause() throws Exception {
        Path logFile = installXxlContext();
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true))
                .thenThrow(new IllegalStateException(TRACKING_CANARY + PHONE_CANARY));

        String proxyReturnValue = job.execute("");

        assertTrue(AopUtils.isAopProxy(job));
        assertNull(proxyReturnValue);
        assertEquals(XxlJobContext.HANDLE_CODE_FAIL, currentXxlContext().getHandleCode());
        String output = currentXxlContext().getHandleMsg();
        assertTrue(output.contains("fulfillment legacy migration batch failed"));
        assertFalse(output.contains(TRACKING_CANARY));
        assertFalse(output.contains(PHONE_CANARY));
        String log = Files.readString(logFile);
        assertTrue(log.contains("fulfillment legacy migration batch failed"));
        assertFalse(log.contains(TRACKING_CANARY));
        assertFalse(log.contains(PHONE_CANARY));
        assertNull(TenantContextHolder.getTenantId());
        verify(migrationService).migrateActiveOrders(TENANT_ID, 0L, 100, true);
        verifyNoInteractions(featureGuard);
    }

    private Path installXxlContext() {
        logFile = Path.of("target", "fulfillment-legacy-migration-" + UUID.randomUUID() + ".log")
                .toAbsolutePath();
        XxlJobContext.setXxlJobContext(new XxlJobContext(81L, "", logFile.toString(), 0, 1));
        return logFile;
    }

    private static XxlJobContext currentXxlContext() {
        return XxlJobContext.getXxlJobContext();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        TenantFrameworkService tenantFrameworkService() {
            return mock(TenantFrameworkService.class);
        }

        @Bean
        FulfillmentLegacyMigrationService migrationService() {
            return mock(FulfillmentLegacyMigrationService.class);
        }

        @Bean
        FulfillmentFeatureGuard fulfillmentFeatureGuard() {
            return mock(FulfillmentFeatureGuard.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        FulfillmentLegacyMigrationJob fulfillmentLegacyMigrationJob(
                FulfillmentLegacyMigrationService migrationService,
                FulfillmentFeatureGuard featureGuard, ObjectMapper objectMapper) {
            return new FulfillmentLegacyMigrationJob(migrationService, featureGuard, objectMapper);
        }

        @Bean
        TenantJobAspect tenantJobAspect(TenantFrameworkService tenantFrameworkService) {
            return new TenantJobAspect(tenantFrameworkService);
        }

    }

}
