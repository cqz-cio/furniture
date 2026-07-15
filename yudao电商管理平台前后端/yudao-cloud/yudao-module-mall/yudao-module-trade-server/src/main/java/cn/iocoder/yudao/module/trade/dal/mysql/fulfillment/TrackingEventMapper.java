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

}
