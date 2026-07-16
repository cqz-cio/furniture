package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FulfillmentFeatureGuard {

    private final FulfillmentProperties properties;

    public void requireReadEnabled() {
        require(properties.isEnabled() && properties.isReadFromNewModel(), "fulfillment reads are disabled");
    }

    public void requireWriteEnabled() {
        require(properties.isEnabled() && properties.isWriteNewModel(), "fulfillment writes are disabled");
    }

    public void requireMigrationWriteEnabled() {
        require(properties.isEnabled() && properties.isWriteNewModel()
                && properties.isLegacyMigrationWriteEnabled(), "fulfillment migration writes are disabled");
    }

    private static void require(boolean enabled, String message) {
        if (!enabled) {
            throw new IllegalStateException(message);
        }
    }

}
