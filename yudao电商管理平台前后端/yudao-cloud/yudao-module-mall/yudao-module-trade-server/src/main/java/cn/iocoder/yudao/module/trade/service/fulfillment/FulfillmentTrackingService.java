package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyTrackingEventCommand;

public interface FulfillmentTrackingService {

    TrackingApplyResult applyEvent(ApplyTrackingEventCommand command);
}
