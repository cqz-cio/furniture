package cn.iocoder.yudao.module.erp.dal.mysql.integration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MallErpProductMappingMapper extends BaseMapperX<MallErpProductMappingDO> {
    default MallErpProductMappingDO selectByMallSkuId(Long mallSkuId) {
        return selectOne(MallErpProductMappingDO::getMallSkuId, mallSkuId);
    }

    default MallErpProductMappingDO selectByErpProductId(Long erpProductId) {
        return selectOne(MallErpProductMappingDO::getErpProductId, erpProductId);
    }

    default List<MallErpProductMappingDO> selectListByMallSkuIds(Collection<Long> mallSkuIds) {
        return selectList(MallErpProductMappingDO::getMallSkuId, mallSkuIds);
    }

    default int deleteByMallSkuIds(Collection<Long> mallSkuIds) {
        return deleteBatch(MallErpProductMappingDO::getMallSkuId, mallSkuIds);
    }
}
