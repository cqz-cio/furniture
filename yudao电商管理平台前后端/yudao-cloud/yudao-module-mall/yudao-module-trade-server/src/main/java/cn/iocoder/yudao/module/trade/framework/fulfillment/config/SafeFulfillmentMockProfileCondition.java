package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class SafeFulfillmentMockProfileCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return FulfillmentConfiguration.isMockAllowed(context.getEnvironment());
    }

}
