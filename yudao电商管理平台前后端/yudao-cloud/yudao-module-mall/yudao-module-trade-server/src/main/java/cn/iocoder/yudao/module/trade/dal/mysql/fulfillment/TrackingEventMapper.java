package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TrackingEventMapper extends BaseMapperX<TrackingEventDO> {

    default List<TrackingEventDO> selectListByShipmentId(Long tenantId, Long shipmentId) {
        return selectList(new LambdaQueryWrapperX<TrackingEventDO>()
                .eq(TrackingEventDO::getTenantId, tenantId)
                .eq(TrackingEventDO::getShipmentId, shipmentId)
                .orderByAsc(TrackingEventDO::getOccurredAt)
                .orderByAsc(TrackingEventDO::getId));
    }

    default List<TrackingEventDO> selectLegacySubjectEvents(Long tenantId, Long shipmentId,
                                                             Long packageId, Long shipmentLegId) {
        LambdaQueryWrapperX<TrackingEventDO> query = new LambdaQueryWrapperX<TrackingEventDO>()
                .eq(TrackingEventDO::getTenantId, tenantId)
                .eq(TrackingEventDO::getShipmentId, shipmentId);
        if (packageId != null) {
            query.and(wrapper -> wrapper.eq(TrackingEventDO::getPackageId, packageId)
                    .or().isNull(TrackingEventDO::getPackageId));
        } else {
            query.isNull(TrackingEventDO::getPackageId);
            if (shipmentLegId != null) {
                query.and(wrapper -> wrapper.eq(TrackingEventDO::getShipmentLegId, shipmentLegId)
                        .or().isNull(TrackingEventDO::getShipmentLegId));
            }
        }
        return selectList(query.orderByAsc(TrackingEventDO::getOccurredAt)
                .orderByAsc(TrackingEventDO::getId));
    }

    default TrackingEventDO selectByExternalEventId(Long tenantId, Long providerId, String externalEventId) {
        return selectOne(new LambdaQueryWrapperX<TrackingEventDO>()
                .eq(TrackingEventDO::getTenantId, tenantId)
                .eq(TrackingEventDO::getProviderId, providerId)
                .eq(TrackingEventDO::getExternalEventId, externalEventId));
    }

    default TrackingEventDO selectByEventHash(Long tenantId, Long providerId, String eventHash) {
        return selectOne(new LambdaQueryWrapperX<TrackingEventDO>()
                .eq(TrackingEventDO::getTenantId, tenantId)
                .eq(TrackingEventDO::getProviderId, providerId)
                .eq(TrackingEventDO::getEventHash, eventHash));
    }

    default TrackingEventDO selectByIdAndTenantId(Long tenantId, Long id) {
        return selectOne(new LambdaQueryWrapperX<TrackingEventDO>()
                .eq(TrackingEventDO::getTenantId, tenantId)
                .eq(TrackingEventDO::getId, id));
    }

}
