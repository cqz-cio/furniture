package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShipmentMapper extends BaseMapperX<ShipmentDO> {

    @Select("SELECT * FROM trade_shipment WHERE tenant_id = #{tenantId} AND id = #{id} "
            + "AND deleted = FALSE FOR UPDATE")
    ShipmentDO selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default List<ShipmentDO> selectListByOrderId(Long tenantId, Long orderId) {
        return selectList(new LambdaQueryWrapperX<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eq(ShipmentDO::getOrderId, orderId)
                .orderByAsc(ShipmentDO::getId));
    }

    default ShipmentDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<ShipmentDO>()
                .eq(ShipmentDO::getId, id)
                .eq(ShipmentDO::getTenantId, tenantId));
    }

    default PageResult<ShipmentDO> selectPage(Long tenantId, ShipmentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eqIfPresent(ShipmentDO::getOrderId, reqVO.getOrderId())
                .eqIfPresent(ShipmentDO::getShipmentNo, reqVO.getShipmentNo())
                .eqIfPresent(ShipmentDO::getShipmentType,
                        reqVO.getShipmentType() == null ? null : reqVO.getShipmentType().name())
                .eqIfPresent(ShipmentDO::getStatus,
                        reqVO.getStatus() == null ? null : reqVO.getStatus().name())
                .eqIfPresent(ShipmentDO::getOriginCountry, reqVO.getOriginCountry())
                .eqIfPresent(ShipmentDO::getDestinationCountry, reqVO.getDestinationCountry())
                .betweenIfPresent(ShipmentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ShipmentDO::getCreateTime)
                .orderByDesc(ShipmentDO::getId));
    }

    default int updateStatusByIdAndVersion(Long tenantId, Long id, Integer version,
                                            String status, LocalDateTime occurredAt) {
        return update(null, new LambdaUpdateWrapper<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eq(ShipmentDO::getId, id)
                .eq(ShipmentDO::getVersion, version)
                .set(ShipmentDO::getStatus, status)
                .set(ShipmentDO::getLastEventOccurredAt, occurredAt)
                .set(ShipmentDO::getVersion, version + 1));
    }

    default int incrementVersionByIdAndVersion(Long tenantId, Long id, Integer version) {
        return update(null, new LambdaUpdateWrapper<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eq(ShipmentDO::getId, id)
                .eq(ShipmentDO::getVersion, version)
                .set(ShipmentDO::getVersion, version + 1));
    }

    default int updateTrackingStateByIdAndVersion(Long tenantId, Long id, Integer version, String status,
                                                   LocalDateTime occurredAt, Integer statusPriority, Long eventId,
                                                   LocalDateTime deliveredAt) {
        return update(null, new LambdaUpdateWrapper<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eq(ShipmentDO::getId, id)
                .eq(ShipmentDO::getVersion, version)
                .set(ShipmentDO::getStatus, status)
                .set(ShipmentDO::getLastEventOccurredAt, occurredAt)
                .set(ShipmentDO::getLastEventStatusPriority, statusPriority)
                .set(ShipmentDO::getLastEventId, eventId)
                .set(deliveredAt != null, ShipmentDO::getDeliveredAt, deliveredAt)
                .set(ShipmentDO::getVersion, version + 1));
    }

}
