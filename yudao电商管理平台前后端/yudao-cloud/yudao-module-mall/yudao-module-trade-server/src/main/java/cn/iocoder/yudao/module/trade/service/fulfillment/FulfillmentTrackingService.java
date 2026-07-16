package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyTrackingEventCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyManualTrackingEventCommand;

public interface FulfillmentTrackingService {

    TrackingApplyResult applyEvent(ApplyTrackingEventCommand command);

    TrackingApplyResult applyManualEvent(String idempotencyKey, ApplyManualTrackingEventCommand command);
}
