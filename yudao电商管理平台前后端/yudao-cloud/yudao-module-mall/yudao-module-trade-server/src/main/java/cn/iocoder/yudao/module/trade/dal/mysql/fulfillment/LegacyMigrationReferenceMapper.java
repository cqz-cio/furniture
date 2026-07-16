package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LegacyMigrationReferenceMapper {

    @Select("SELECT COUNT(*) FROM erp_warehouse WHERE tenant_id = #{tenantId} "
            + "AND id = #{warehouseId} AND status = 0 AND deleted = FALSE")
    long countEnabledWarehouse(@Param("tenantId") Long tenantId, @Param("warehouseId") Long warehouseId);

}
