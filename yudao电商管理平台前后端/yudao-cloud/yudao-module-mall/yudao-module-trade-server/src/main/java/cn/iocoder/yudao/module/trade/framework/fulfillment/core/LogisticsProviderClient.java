package cn.iocoder.yudao.module.trade.framework.fulfillment.core;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingQuery;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationResult;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingSnapshot;

import java.util.Set;

public interface LogisticsProviderClient {

    String getProviderCode();

    Set<ProviderCapability> getCapabilities();

    TrackingRegistrationResult registerTracking(TrackingRegistrationCommand command);

    TrackingSnapshot queryTracking(TrackingQuery query);

}
