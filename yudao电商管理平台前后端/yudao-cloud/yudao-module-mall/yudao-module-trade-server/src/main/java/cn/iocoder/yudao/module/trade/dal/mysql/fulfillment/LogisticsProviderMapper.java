package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LogisticsProviderMapper extends BaseMapperX<LogisticsProviderDO> {

    default LogisticsProviderDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<LogisticsProviderDO>()
                .eq(LogisticsProviderDO::getId, id)
                .eq(LogisticsProviderDO::getTenantId, tenantId));
    }

    @Select("SELECT * FROM trade_logistics_provider WHERE tenant_id = #{tenantId} AND id = #{id} "
            + "AND deleted = FALSE FOR UPDATE")
    LogisticsProviderDO selectByIdAndTenantIdForUpdate(@Param("id") Long id,
                                                       @Param("tenantId") Long tenantId);
}
