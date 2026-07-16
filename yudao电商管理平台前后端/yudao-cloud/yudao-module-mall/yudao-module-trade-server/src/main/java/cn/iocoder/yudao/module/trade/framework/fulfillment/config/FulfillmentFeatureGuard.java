package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;

@Component
@RequiredArgsConstructor
public class FulfillmentFeatureGuard {

    private final FulfillmentProperties properties;

    public void requireReadEnabled() {
        require(properties.isEnabled() && properties.isReadFromNewModel());
    }

    public void requireWriteEnabled() {
        require(properties.isEnabled() && properties.isWriteNewModel());
    }

    public void requireMigrationWriteEnabled() {
        require(properties.isEnabled() && properties.isWriteNewModel()
                && properties.isLegacyMigrationWriteEnabled());
    }

    private static void require(boolean enabled) {
        if (!enabled) {
            throw exception(FULFILLMENT_FEATURE_DISABLED);
        }
    }

}
