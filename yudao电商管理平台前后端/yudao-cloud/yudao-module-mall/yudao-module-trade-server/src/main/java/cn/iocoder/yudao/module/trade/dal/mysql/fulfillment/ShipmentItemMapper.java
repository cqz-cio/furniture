package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ShipmentItemMapper extends BaseMapperX<ShipmentItemDO> {

    default List<ShipmentItemDO> selectListByShipmentId(Long tenantId, Long shipmentId) {
        return selectList(new LambdaQueryWrapperX<ShipmentItemDO>()
                .eq(ShipmentItemDO::getTenantId, tenantId)
                .eq(ShipmentItemDO::getShipmentId, shipmentId)
                .orderByAsc(ShipmentItemDO::getId));
    }

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM trade_shipment_item "
            + "WHERE tenant_id = #{tenantId} AND order_item_id = #{orderItemId} AND deleted = FALSE")
    BigDecimal sumQuantityByOrderItemId(@Param("tenantId") Long tenantId,
                                        @Param("orderItemId") Long orderItemId);

}
