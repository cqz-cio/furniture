package cn.iocoder.yudao.module.erp.dal.mysql.integration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MallErpProductMappingMapper extends BaseMapperX<MallErpProductMappingDO> {
    default MallErpProductMappingDO selectByMallSkuId(Long mallSkuId) {
        return selectOne(MallErpProductMappingDO::getMallSkuId, mallSkuId);
    }
}
