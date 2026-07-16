package cn.iocoder.yudao.module.trade.job.fulfillment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.FulfillmentLegacyMigrationService;
import cn.iocoder.yudao.module.trade.service.fulfillment.migration.MigrationBatchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class FulfillmentLegacyMigrationJob {

    private static final long DEFAULT_AFTER_ORDER_ID = 0L;
    private static final int DEFAULT_LIMIT = 100;
    private static final boolean DEFAULT_DRY_RUN = true;
    private static final Set<String> ALLOWED_PARAMETERS = Set.of("afterOrderId", "limit", "dryRun");
    private static final String INVALID_PARAMETERS = "invalid fulfillment legacy migration job parameters";

    private final FulfillmentLegacyMigrationService migrationService;
    private final FulfillmentFeatureGuard featureGuard;
    private final ObjectMapper objectMapper;

    @XxlJob("fulfillmentLegacyMigrationJob")
    @TenantJob
    public String execute(String param) {
        MigrationJobParameters parameters = parseParameters(param);
        if (!parameters.dryRun()) {
            featureGuard.requireMigrationWriteEnabled();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        MigrationBatchResult result;
        try {
            result = migrationService.migrateActiveOrders(tenantId, parameters.afterOrderId(),
                    parameters.limit(), parameters.dryRun());
        } catch (RuntimeException ignored) {
            throw new IllegalStateException("fulfillment legacy migration batch failed");
        }
        return serializeSummary(result);
    }

    private MigrationJobParameters parseParameters(String param) {
        if (StrUtil.isBlank(param)) {
            return MigrationJobParameters.defaults();
        }
        try {
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(param);
            if (root == null || !root.isObject() || containsUnknownField(root)) {
                throw invalidParameters();
            }
            long afterOrderId = readLong(root, "afterOrderId", DEFAULT_AFTER_ORDER_ID);
            int limit = readInt(root, "limit", DEFAULT_LIMIT);
            boolean dryRun = readBoolean(root, "dryRun", DEFAULT_DRY_RUN);
            if (afterOrderId < 0 || limit < 1 || limit > 100) {
                throw invalidParameters();
            }
            return new MigrationJobParameters(afterOrderId, limit, dryRun);
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            throw invalidParameters();
        }
    }

    private static boolean containsUnknownField(JsonNode root) {
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            if (!ALLOWED_PARAMETERS.contains(fieldNames.next())) {
                return true;
            }
        }
        return false;
    }

    private static long readLong(JsonNode root, String field, long defaultValue) {
        JsonNode value = root.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidParameters();
        }
        return value.longValue();
    }

    private static int readInt(JsonNode root, String field, int defaultValue) {
        JsonNode value = root.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidParameters();
        }
        return value.intValue();
    }

    private static boolean readBoolean(JsonNode root, String field, boolean defaultValue) {
        JsonNode value = root.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw invalidParameters();
        }
        return value.booleanValue();
    }

    private String serializeSummary(MigrationBatchResult result) {
        Map<String, Long> reasonCounts = new TreeMap<>();
        result.orders().forEach(order -> reasonCounts.merge(order.outcome().name(), 1L, Long::sum));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dryRun", result.dryRun());
        summary.put("scanned", result.scanned());
        summary.put("wouldMigrate", result.wouldMigrate());
        summary.put("migrated", result.migrated());
        summary.put("alreadyMigrated", result.alreadyMigrated());
        summary.put("rejected", result.rejected());
        summary.put("nextAfterOrderId", result.nextAfterOrderId());
        summary.put("hasMore", result.hasMore());
        summary.put("reasonCounts", reasonCounts);
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ignored) {
            throw new IllegalStateException("fulfillment legacy migration summary failed");
        }
    }

    private static IllegalArgumentException invalidParameters() {
        return new IllegalArgumentException(INVALID_PARAMETERS);
    }

    private record MigrationJobParameters(long afterOrderId, int limit, boolean dryRun) {

        private static MigrationJobParameters defaults() {
            return new MigrationJobParameters(DEFAULT_AFTER_ORDER_ID, DEFAULT_LIMIT, DEFAULT_DRY_RUN);
        }

    }

}
