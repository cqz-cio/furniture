package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "yudao.trade.fulfillment")
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

}
