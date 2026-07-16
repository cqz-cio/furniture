package cn.iocoder.yudao.module.trade.service.fulfillment;

public interface FulfillmentLegacyProjectionService {

    FulfillmentLegacyProjectionResult project(Long tenantId, Long orderId);

}
