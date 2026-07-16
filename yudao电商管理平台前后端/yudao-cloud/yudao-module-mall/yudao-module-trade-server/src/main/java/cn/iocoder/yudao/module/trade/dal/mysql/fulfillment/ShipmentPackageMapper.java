package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShipmentPackageMapper extends BaseMapperX<ShipmentPackageDO> {

    default ShipmentPackageDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<ShipmentPackageDO>()
                .eq(ShipmentPackageDO::getId, id)
                .eq(ShipmentPackageDO::getTenantId, tenantId));
    }

    default List<ShipmentPackageDO> selectListByShipmentId(Long tenantId, Long shipmentId) {
        return selectList(new LambdaQueryWrapperX<ShipmentPackageDO>()
                .eq(ShipmentPackageDO::getTenantId, tenantId)
                .eq(ShipmentPackageDO::getShipmentId, shipmentId)
                .orderByAsc(ShipmentPackageDO::getId));
    }

    default ShipmentPackageDO selectByCarrierIdAndTrackingNumber(Long tenantId, Long carrierId,
                                                                  String trackingNumber) {
        return selectOne(new LambdaQueryWrapperX<ShipmentPackageDO>()
                .eq(ShipmentPackageDO::getTenantId, tenantId)
                .eq(ShipmentPackageDO::getCarrierId, carrierId)
                .eq(ShipmentPackageDO::getTrackingNumber, trackingNumber));
    }

    @Select("SELECT * FROM trade_shipment_package WHERE tenant_id = #{tenantId} "
            + "AND carrier_id = #{carrierId} AND tracking_number = #{trackingNumber} "
            + "AND deleted = FALSE FOR UPDATE")
    ShipmentPackageDO selectByCarrierIdAndTrackingNumberForUpdate(@Param("tenantId") Long tenantId,
                                                                   @Param("carrierId") Long carrierId,
                                                                   @Param("trackingNumber") String trackingNumber);

    default int updateStatusByIdAndVersion(Long tenantId, Long id, Integer version, String status) {
        return update(null, new LambdaUpdateWrapper<ShipmentPackageDO>()
                .eq(ShipmentPackageDO::getTenantId, tenantId)
                .eq(ShipmentPackageDO::getId, id)
                .eq(ShipmentPackageDO::getVersion, version)
                .set(ShipmentPackageDO::getStatus, status)
                .set(ShipmentPackageDO::getVersion, version + 1));
    }


    default int updateTrackingStateByIdAndVersion(Long tenantId, Long id, Integer version, String status,
                                                   LocalDateTime occurredAt, Integer statusPriority, Long eventId) {
        return update(null, new LambdaUpdateWrapper<ShipmentPackageDO>()
                .eq(ShipmentPackageDO::getTenantId, tenantId)
                .eq(ShipmentPackageDO::getId, id)
                .eq(ShipmentPackageDO::getVersion, version)
                .set(ShipmentPackageDO::getStatus, status)
                .set(ShipmentPackageDO::getLastEventOccurredAt, occurredAt)
                .set(ShipmentPackageDO::getLastEventStatusPriority, statusPriority)
                .set(ShipmentPackageDO::getLastEventId, eventId)
                .set(ShipmentPackageDO::getVersion, version + 1));
    }
}
