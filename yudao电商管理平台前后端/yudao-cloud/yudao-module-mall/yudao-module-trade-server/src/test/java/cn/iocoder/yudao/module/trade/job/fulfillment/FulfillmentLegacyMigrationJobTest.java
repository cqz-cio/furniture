package cn.iocoder.yudao.module.trade.job.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.FulfillmentLegacyMigrationService;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationBatchResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationOrderResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FulfillmentLegacyMigrationJobTest {

    private static final Long TENANT_ID = 121L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FulfillmentLegacyMigrationService migrationService;
    private FulfillmentFeatureGuard featureGuard;
    private FulfillmentLegacyMigrationJob job;

    @BeforeEach
    void setUp() {
        migrationService = mock(FulfillmentLegacyMigrationService.class);
        featureGuard = mock(FulfillmentFeatureGuard.class);
        job = new FulfillmentLegacyMigrationJob(migrationService, featureGuard, OBJECT_MAPPER);
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void executeShouldExposeXxlAndTenantJobContract() throws Exception {
        Method execute = FulfillmentLegacyMigrationJob.class.getMethod("execute", String.class);
        XxlJob xxlJob = execute.getAnnotation(XxlJob.class);

        assertNotNull(xxlJob);
        assertEquals("fulfillmentLegacyMigrationJob", xxlJob.value());
        assertNotNull(execute.getAnnotation(TenantJob.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{}"})
    void executeShouldUseSafeDefaults(String param) throws Exception {
        MigrationBatchResult result = result(true, false);
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true)).thenReturn(result);

        JsonNode output = OBJECT_MAPPER.readTree(job.execute(param));

        assertEquals(true, output.get("dryRun").asBoolean());
        assertEquals(1, output.get("scanned").asInt());
        verify(migrationService).migrateActiveOrders(TENANT_ID, 0L, 100, true);
    }

    @Test
    void executeShouldProcessExactlyOneBoundedBatchFromTenantContext() throws Exception {
        MigrationBatchResult result = result(true, true);
        when(migrationService.migrateActiveOrders(TENANT_ID, 41L, 7, true)).thenReturn(result);

        JsonNode output = OBJECT_MAPPER.readTree(
                job.execute("{\"afterOrderId\":41,\"limit\":7,\"dryRun\":true}"));

        assertTrue(output.get("hasMore").asBoolean());
        verify(migrationService, times(1)).migrateActiveOrders(TENANT_ID, 41L, 7, true);
        verifyNoMoreInteractions(migrationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"afterOrderId\":-1}",
            "{\"limit\":0}",
            "{\"limit\":101}",
            "[]",
            "null",
            "not-json",
            "{} {}",
            "{} trailing-garbage",
            "{\"tenantId\":999}",
            "{\"tenant_id\":999}",
            "{\"unknown\":true}",
            "{\"afterOrderId\":1.5}",
            "{\"limit\":1.5}",
            "{\"dryRun\":\"true\"}"
    })
    void executeShouldRejectInvalidOrTenantSuppliedParameters(String param) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> job.execute(param));

        assertEquals("invalid fulfillment legacy migration job parameters", error.getMessage());
        verifyNoInteractions(migrationService);
    }

    @Test
    void executeShouldAllowDryRunWhenAllWriteFlagsAreFalse() {
        MigrationBatchResult result = result(true, false);
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true)).thenReturn(result);

        job.execute("{\"dryRun\":true}");

        verify(migrationService).migrateActiveOrders(TENANT_ID, 0L, 100, true);
        verifyNoInteractions(featureGuard);
    }

    @Test
    void executeShouldGuardWriteExactlyOnceBeforeCallingMigrationService() {
        MigrationBatchResult result = result(false, false);
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, false)).thenReturn(result);

        job.execute("{\"dryRun\":false}");

        verify(featureGuard, times(1)).requireMigrationWriteEnabled();
        verify(migrationService).migrateActiveOrders(TENANT_ID, 0L, 100, false);
    }

    @Test
    void executeShouldExposeStableFeatureDisabledErrorBeforeMigrationService() {
        doThrow(exception(FULFILLMENT_FEATURE_DISABLED))
                .when(featureGuard).requireMigrationWriteEnabled();

        assertServiceException(() -> job.execute("{\"dryRun\":false}"),
                FULFILLMENT_FEATURE_DISABLED);

        verify(featureGuard).requireMigrationWriteEnabled();
        verifyNoInteractions(migrationService);
    }

    @Test
    void executeShouldReturnOnlySafeFieldsForTenantJobXxlSummary() throws Exception {
        MigrationBatchResult result = new MigrationBatchResult(true, 3, 1, 0, 1, 1,
                987654321L, true, List.of(
                new MigrationOrderResult(111111L, MigrationOutcome.WOULD_MIGRATE, "TRACKING_CANARY"),
                new MigrationOrderResult(222222L, MigrationOutcome.ALREADY_MIGRATED, "PHONE_CANARY"),
                new MigrationOrderResult(333333L, MigrationOutcome.BLANK_TRACKING, "FACTS_CANARY")));
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true)).thenReturn(result);

        String serialized = job.execute("");
        JsonNode output = OBJECT_MAPPER.readTree(serialized);

        Set<String> outputFields = new HashSet<>();
        output.fieldNames().forEachRemaining(outputFields::add);
        assertEquals(Set.of("dryRun", "scanned", "wouldMigrate", "migrated", "alreadyMigrated",
                "rejected", "nextAfterOrderId", "hasMore", "reasonCounts"), outputFields);
        assertEquals(987654321L, output.get("nextAfterOrderId").asLong());
        assertEquals(1, output.get("reasonCounts").get("WOULD_MIGRATE").asInt());
        assertEquals(1, output.get("reasonCounts").get("ALREADY_MIGRATED").asInt());
        assertEquals(1, output.get("reasonCounts").get("BLANK_TRACKING").asInt());
        assertFalse(output.has("orders"));
        assertFalse(serialized.contains("111111"));
        assertFalse(serialized.contains("222222"));
        assertFalse(serialized.contains("333333"));
        assertFalse(serialized.contains("TRACKING_CANARY"));
        assertFalse(serialized.contains("PHONE_CANARY"));
        assertFalse(serialized.contains("FACTS_CANARY"));
    }

    @Test
    void executeShouldExposeCauseFreeSafeErrorToTenantJobXxlLogging() {
        when(migrationService.migrateActiveOrders(TENANT_ID, 0L, 100, true))
                .thenThrow(new IllegalStateException("TRACKING_PHONE_FACT_DIGEST_CANARY"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> job.execute(""));

        assertEquals("fulfillment legacy migration batch failed", error.getMessage());
        assertEquals(null, error.getCause());
        assertFalse(error.toString().contains("CANARY"));
    }

    private static MigrationBatchResult result(boolean dryRun, boolean hasMore) {
        return new MigrationBatchResult(dryRun, 1, dryRun ? 1 : 0, dryRun ? 0 : 1,
                0, 0, 42L, hasMore,
                List.of(MigrationOrderResult.of(42L,
                        dryRun ? MigrationOutcome.WOULD_MIGRATE : MigrationOutcome.MIGRATED)));
    }

}
