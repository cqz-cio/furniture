package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FulfillmentTrackingRegistrationFailureService {

    private final FulfillmentOutboxEventMapper outboxMapper;
    private final FulfillmentFeatureGuard featureGuard;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRetry(Long tenantId, Long shipmentId, Long packageId, Long providerId) {
        featureGuard.requireWriteEnabled();
        TenantUtils.execute(tenantId, () -> outboxMapper.insert(new FulfillmentOutboxEventDO()
                .setTenantId(tenantId)
                .setEventId(UUID.randomUUID().toString())
                .setAggregateType("SHIPMENT")
                .setAggregateId(shipmentId)
                .setEventType("TRACKING_REGISTRATION_RETRY")
                .setPayload(Map.of(
                        "tenantId", tenantId,
                        "shipmentId", shipmentId,
                        "packageId", packageId,
                        "providerId", providerId))
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setNextAttemptAt(LocalDateTime.now(Clock.systemUTC()))));
    }

}
