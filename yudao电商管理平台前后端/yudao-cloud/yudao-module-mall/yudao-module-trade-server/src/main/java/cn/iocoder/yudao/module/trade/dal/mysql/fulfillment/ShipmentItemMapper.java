package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ShipmentItemMapper extends BaseMapperX<ShipmentItemDO> {

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM trade_shipment_item "
            + "WHERE tenant_id = #{tenantId} AND order_item_id = #{orderItemId} AND deleted = FALSE")
    BigDecimal sumQuantityByOrderItemId(@Param("tenantId") Long tenantId,
                                        @Param("orderItemId") Long orderItemId);

}
