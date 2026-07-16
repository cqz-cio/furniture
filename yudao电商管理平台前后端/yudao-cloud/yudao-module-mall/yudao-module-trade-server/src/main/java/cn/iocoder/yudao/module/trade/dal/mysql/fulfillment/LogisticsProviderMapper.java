package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogisticsProviderMapper extends BaseMapperX<LogisticsProviderDO> {

    default LogisticsProviderDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<LogisticsProviderDO>()
                .eq(LogisticsProviderDO::getId, id)
                .eq(LogisticsProviderDO::getTenantId, tenantId));
    }
}
