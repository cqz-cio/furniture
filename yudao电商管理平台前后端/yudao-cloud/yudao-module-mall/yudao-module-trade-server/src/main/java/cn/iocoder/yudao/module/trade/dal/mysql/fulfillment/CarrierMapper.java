package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CarrierMapper extends BaseMapperX<CarrierDO> {

    default CarrierDO selectByIdAndTenantId(Long id, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<CarrierDO>()
                .eq(CarrierDO::getId, id)
                .eq(CarrierDO::getTenantId, tenantId));
    }
}
