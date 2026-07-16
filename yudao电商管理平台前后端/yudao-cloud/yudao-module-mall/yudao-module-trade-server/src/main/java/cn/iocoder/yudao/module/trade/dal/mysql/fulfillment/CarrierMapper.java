package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CarrierMapper extends BaseMapperX<CarrierDO> {

    default CarrierDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<CarrierDO>()
                .eq(CarrierDO::getId, id)
                .eq(CarrierDO::getTenantId, tenantId));
    }

    @Select("SELECT * FROM trade_carrier WHERE tenant_id = #{tenantId} "
            + "AND legacy_express_id = #{legacyExpressId} AND status = 0 AND deleted = FALSE ORDER BY id ASC")
    List<CarrierDO> selectEnabledByLegacyExpressId(@Param("tenantId") Long tenantId,
                                                    @Param("legacyExpressId") Long legacyExpressId);

    @Select("SELECT * FROM trade_carrier WHERE tenant_id = #{tenantId} "
            + "AND legacy_express_id = #{legacyExpressId} AND status = 0 AND deleted = FALSE "
            + "ORDER BY id ASC FOR UPDATE")
    List<CarrierDO> selectEnabledByLegacyExpressIdForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("legacyExpressId") Long legacyExpressId);
}
