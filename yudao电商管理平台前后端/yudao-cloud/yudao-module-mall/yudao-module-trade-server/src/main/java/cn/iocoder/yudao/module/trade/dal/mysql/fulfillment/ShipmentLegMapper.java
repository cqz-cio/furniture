package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShipmentLegMapper extends BaseMapperX<ShipmentLegDO> {

    default List<ShipmentLegDO> selectListByShipmentId(Long tenantId, Long shipmentId) {
        return selectList(new LambdaQueryWrapperX<ShipmentLegDO>()
                .eq(ShipmentLegDO::getTenantId, tenantId)
                .eq(ShipmentLegDO::getShipmentId, shipmentId)
                .orderByAsc(ShipmentLegDO::getSequenceNo)
                .orderByAsc(ShipmentLegDO::getId));
    }

    default int updateStatusByIdAndVersion(Long tenantId, Long id, Integer version, String status,
                                            LocalDateTime startedAt) {
        return update(null, new LambdaUpdateWrapper<ShipmentLegDO>()
                .eq(ShipmentLegDO::getTenantId, tenantId)
                .eq(ShipmentLegDO::getId, id)
                .eq(ShipmentLegDO::getVersion, version)
                .set(ShipmentLegDO::getStatus, status)
                .set(ShipmentLegDO::getStartedAt, startedAt)
                .set(ShipmentLegDO::getVersion, version + 1));
    }
}
