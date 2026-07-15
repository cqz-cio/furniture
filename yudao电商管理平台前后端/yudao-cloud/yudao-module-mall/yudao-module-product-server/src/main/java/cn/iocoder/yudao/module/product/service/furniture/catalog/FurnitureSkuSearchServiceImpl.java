package cn.iocoder.yudao.module.product.service.furniture.catalog;

import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.mysql.furniture.FurnitureSkuSearchMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class FurnitureSkuSearchServiceImpl implements FurnitureSkuSearchService {

    @Resource
    private FurnitureSkuSearchMapper mapper;
    @Resource
    private ProductSkuService productSkuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(FurnitureSkuSearchDO value) {
        ProductSkuDO sku = productSkuService.getSku(value.getSkuId());
        if (sku == null || !Objects.equals(sku.getSpuId(), value.getSpuId())) {
            throw new IllegalArgumentException("Furniture projection SKU/SPU mismatch");
        }
        FurnitureProjectionValidator.validate(value.getCategoryCode(), value.getStyleCodes(),
                value.getColorCode(), value.getMaterialCodes(), value.getSeatCount(),
                value.getWidthMm(), value.getDepthMm(), value.getHeightMm(),
                value.getRoomTypeCodes(), value.getFeatureCodes());
        FurnitureSkuSearchDO current = mapper.selectBySkuId(value.getSkuId());
        if (current == null) {
            mapper.insert(value);
        } else {
            value.setId(current.getId());
            mapper.updateById(value);
        }
    }

    @Override
    public List<FurnitureSkuSearchDO> getByCategory(String categoryCode) {
        return mapper.selectByCategory(categoryCode);
    }

    @Override
    public List<FurnitureSkuSearchDO> getBySkuIds(Collection<Long> skuIds) {
        return mapper.selectBySkuIds(skuIds);
    }

}
