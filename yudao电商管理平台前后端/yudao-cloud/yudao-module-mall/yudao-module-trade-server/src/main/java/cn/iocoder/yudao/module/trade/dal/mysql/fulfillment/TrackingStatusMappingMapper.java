package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingStatusMappingDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface TrackingStatusMappingMapper extends BaseMapperX<TrackingStatusMappingDO> {

    default TrackingStatusMappingDO selectActive(Long tenantId, String providerCode,
                                                   String carrierCode, String normalizedStatus,
                                                   LocalDateTime receivedAtUtc) {
        return selectOne(new LambdaQueryWrapperX<TrackingStatusMappingDO>()
                .eq(TrackingStatusMappingDO::getTenantId, tenantId)
                .eq(TrackingStatusMappingDO::getProviderCode, providerCode)
                .eq(TrackingStatusMappingDO::getCarrierCode, carrierCode)
                .eq(TrackingStatusMappingDO::getProviderStatusNormalized, normalizedStatus)
                .le(TrackingStatusMappingDO::getEffectiveAt, receivedAtUtc)
                .orderByDesc(TrackingStatusMappingDO::getEffectiveAt)
                .orderByDesc(TrackingStatusMappingDO::getId)
                .last("LIMIT 1"));
    }

    default TrackingStatusMappingDO selectAtVersion(Long tenantId, String providerCode,
                                                     String carrierCode, String normalizedStatus,
                                                     String mappingVersion) {
        return selectOne(new LambdaQueryWrapperX<TrackingStatusMappingDO>()
                .eq(TrackingStatusMappingDO::getTenantId, tenantId)
                .eq(TrackingStatusMappingDO::getProviderCode, providerCode)
                .eq(TrackingStatusMappingDO::getCarrierCode, carrierCode)
                .eq(TrackingStatusMappingDO::getProviderStatusNormalized, normalizedStatus)
                .eq(TrackingStatusMappingDO::getMappingVersion, mappingVersion));
    }

}
