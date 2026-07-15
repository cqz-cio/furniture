package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderFulfillmentSummaryMapper extends BaseMapperX<OrderFulfillmentSummaryDO> {

    default int updateCountsAndStatusByIdAndVersion(Long tenantId, Long id, Integer version, String status,
                                                     Integer shipmentCount, Integer deliveredShipmentCount) {
        return update(null, new LambdaUpdateWrapper<OrderFulfillmentSummaryDO>()
                .eq(OrderFulfillmentSummaryDO::getTenantId, tenantId)
                .eq(OrderFulfillmentSummaryDO::getId, id)
                .eq(OrderFulfillmentSummaryDO::getVersion, version)
                .set(OrderFulfillmentSummaryDO::getStatus, status)
                .set(OrderFulfillmentSummaryDO::getShipmentCount, shipmentCount)
                .set(OrderFulfillmentSummaryDO::getDeliveredShipmentCount, deliveredShipmentCount)
                .set(OrderFulfillmentSummaryDO::getVersion, version + 1));
    }

}
