package cn.iocoder.yudao.module.product.service.furniture.catalog;

import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;

import java.util.Collection;
import java.util.List;

public interface FurnitureSkuSearchService {

    void upsert(FurnitureSkuSearchDO projection);

    List<FurnitureSkuSearchDO> getByCategory(String categoryCode);

    List<FurnitureSkuSearchDO> getBySkuIds(Collection<Long> skuIds);

}
