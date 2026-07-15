package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShipmentMapper extends BaseMapperX<ShipmentDO> {

    default List<ShipmentDO> selectListByOrderId(Long tenantId, Long orderId) {
        return selectList(new LambdaQueryWrapperX<ShipmentDO>()
                .eq(ShipmentDO::getTenantId, tenantId)
                .eq(ShipmentDO::getOrderId, orderId)
                .orderByAsc(ShipmentDO::getId));
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

}
