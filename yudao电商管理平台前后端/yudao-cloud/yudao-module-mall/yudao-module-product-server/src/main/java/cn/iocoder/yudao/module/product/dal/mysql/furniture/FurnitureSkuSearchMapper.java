package cn.iocoder.yudao.module.product.dal.mysql.furniture;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FurnitureSkuSearchMapper extends BaseMapperX<FurnitureSkuSearchDO> {

    default FurnitureSkuSearchDO selectBySkuId(Long skuId) {
        return selectOne(FurnitureSkuSearchDO::getSkuId, skuId);
    }

    default List<FurnitureSkuSearchDO> selectByCategory(String categoryCode) {
        return selectList(FurnitureSkuSearchDO::getCategoryCode, categoryCode);
    }

    default List<FurnitureSkuSearchDO> selectBySkuIds(Collection<Long> skuIds) {
        return selectList(FurnitureSkuSearchDO::getSkuId, skuIds);
    }

    default int deleteBySkuIds(Collection<Long> skuIds) {
        return deleteBatch(FurnitureSkuSearchDO::getSkuId, skuIds);
    }

}
