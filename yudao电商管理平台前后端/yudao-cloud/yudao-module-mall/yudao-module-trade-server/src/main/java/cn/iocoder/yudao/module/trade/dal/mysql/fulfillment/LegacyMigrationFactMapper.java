package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LegacyMigrationFactDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LegacyMigrationFactMapper extends BaseMapperX<LegacyMigrationFactDO> {

    @Select("SELECT * FROM trade_fulfillment_legacy_migration_fact "
            + "WHERE tenant_id = #{tenantId} AND order_id = #{orderId} AND deleted = FALSE")
    LegacyMigrationFactDO selectActiveByOrderId(@Param("tenantId") Long tenantId,
                                                 @Param("orderId") Long orderId);

}
