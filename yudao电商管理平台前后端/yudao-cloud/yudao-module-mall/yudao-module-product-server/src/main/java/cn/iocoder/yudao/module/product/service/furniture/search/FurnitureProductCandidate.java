package cn.iocoder.yudao.module.product.service.furniture.search;

import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;

import java.math.BigDecimal;

public class FurnitureProductCandidate {

    private final FurnitureSkuSearchDO projection;
    private final ProductSkuDO sku;
    private final ProductSpuDO spu;
    private final BigDecimal sellableStock;
    private final boolean erpMapped;

    public FurnitureProductCandidate(FurnitureSkuSearchDO projection, ProductSkuDO sku, ProductSpuDO spu,
                                     BigDecimal sellableStock, boolean erpMapped) {
        this.projection = projection;
        this.sku = sku;
        this.spu = spu;
        this.sellableStock = sellableStock;
        this.erpMapped = erpMapped;
    }

    public FurnitureSkuSearchDO getProjection() {
        return projection;
    }

    public ProductSkuDO getSku() {
        return sku;
    }

    public ProductSpuDO getSpu() {
        return spu;
    }

    public BigDecimal getSellableStock() {
        return sellableStock;
    }

    public boolean isErpMapped() {
        return erpMapped;
    }
}
