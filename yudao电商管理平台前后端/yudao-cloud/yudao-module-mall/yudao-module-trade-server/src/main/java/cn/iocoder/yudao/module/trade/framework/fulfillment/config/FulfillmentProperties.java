package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "yudao.trade.fulfillment")
@Validated
@Data
@ToString
public class FulfillmentProperties {

    private boolean enabled;
    private boolean writeNewModel;
    private boolean readFromNewModel;
    private boolean customerUiEnabled;
    private boolean legacyMigrationWriteEnabled;
    private String providerCode = "";
    @ToString.Exclude
    private String idempotencyHmacKey = "";

    @AssertTrue(message = "write-new-model requires enabled, provider-code and idempotency-hmac-key")
    public boolean isWriteConfigurationValid() {
        return !writeNewModel || enabled && hasText(providerCode) && hasText(idempotencyHmacKey);
    }

    @AssertTrue(message = "read-from-new-model requires enabled")
    public boolean isReadConfigurationValid() {
        return !readFromNewModel || enabled;
    }

    @AssertTrue(message = "customer-ui-enabled requires enabled and read-from-new-model")
    public boolean isCustomerUiConfigurationValid() {
        return !customerUiEnabled || enabled && readFromNewModel;
    }

    @AssertTrue(message = "legacy-migration-write-enabled requires enabled and write-new-model")
    public boolean isLegacyMigrationWriteConfigurationValid() {
        return !legacyMigrationWriteEnabled || enabled && writeNewModel;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
