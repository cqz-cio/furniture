package cn.iocoder.yudao.module.trade.service.fulfillment;

public record TrackingApplyResult(boolean inserted, boolean stateChanged,
                                  String previousStatus, String currentStatus) {
}
