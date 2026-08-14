package cn.iocoder.yudao.module.erp.service.common;

import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseInItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseReturnItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOutItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleReturnItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockCheckItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockInItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockMoveItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockOutItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockRecordDO;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpProductMappingMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.purchase.ErpPurchaseInItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.purchase.ErpPurchaseOrderItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.purchase.ErpPurchaseReturnItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.sale.ErpSaleOrderItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.sale.ErpSaleOutItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.sale.ErpSaleReturnItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockCheckItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockInItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockMoveItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockOutItemMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.erp.enums.ErrorCodeConstants.PRODUCT_DELETE_FAIL_BUSINESS_REFERENCE;
import static cn.iocoder.yudao.module.erp.enums.ErrorCodeConstants.PRODUCT_DELETE_FAIL_MALL_MAPPING;
import static cn.iocoder.yudao.module.erp.enums.ErrorCodeConstants.PRODUCT_DELETE_FAIL_STOCK;
import static cn.iocoder.yudao.module.erp.enums.ErrorCodeConstants.WAREHOUSE_DELETE_FAIL_BUSINESS_REFERENCE;
import static cn.iocoder.yudao.module.erp.enums.ErrorCodeConstants.WAREHOUSE_DELETE_FAIL_STOCK;

/**
 * ERP 主数据删除前的引用校验。
 *
 * 产品和仓库一旦进入库存流水或业务单据，就应通过“停用”退出使用，不能再物理/逻辑删除主数据。
 */
@Service
public class ErpReferenceValidationService {

    @Resource
    private MallErpProductMappingMapper productMappingMapper;
    @Resource
    private ErpStockMapper stockMapper;
    @Resource
    private ErpStockRecordMapper stockRecordMapper;
    @Resource
    private ErpStockInItemMapper stockInItemMapper;
    @Resource
    private ErpStockOutItemMapper stockOutItemMapper;
    @Resource
    private ErpStockMoveItemMapper stockMoveItemMapper;
    @Resource
    private ErpStockCheckItemMapper stockCheckItemMapper;
    @Resource
    private ErpPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Resource
    private ErpPurchaseInItemMapper purchaseInItemMapper;
    @Resource
    private ErpPurchaseReturnItemMapper purchaseReturnItemMapper;
    @Resource
    private ErpSaleOrderItemMapper saleOrderItemMapper;
    @Resource
    private ErpSaleOutItemMapper saleOutItemMapper;
    @Resource
    private ErpSaleReturnItemMapper saleReturnItemMapper;

    public void validateProductDeletable(Long productId) {
        if (productMappingMapper.selectCountByErpProductId(productId) > 0) {
            throw exception(PRODUCT_DELETE_FAIL_MALL_MAPPING);
        }
        if (stockMapper.selectCount(ErpStockDO::getProductId, productId) > 0) {
            throw exception(PRODUCT_DELETE_FAIL_STOCK);
        }
        if (hasProductBusinessReference(productId)) {
            throw exception(PRODUCT_DELETE_FAIL_BUSINESS_REFERENCE);
        }
    }

    public void validateWarehouseDeletable(Long warehouseId) {
        if (stockMapper.selectCount(ErpStockDO::getWarehouseId, warehouseId) > 0) {
            throw exception(WAREHOUSE_DELETE_FAIL_STOCK);
        }
        if (hasWarehouseBusinessReference(warehouseId)) {
            throw exception(WAREHOUSE_DELETE_FAIL_BUSINESS_REFERENCE);
        }
    }

    private boolean hasProductBusinessReference(Long productId) {
        return stockRecordMapper.selectCount(ErpStockRecordDO::getProductId, productId) > 0
                || stockInItemMapper.selectCount(ErpStockInItemDO::getProductId, productId) > 0
                || stockOutItemMapper.selectCount(ErpStockOutItemDO::getProductId, productId) > 0
                || stockMoveItemMapper.selectCount(ErpStockMoveItemDO::getProductId, productId) > 0
                || stockCheckItemMapper.selectCount(ErpStockCheckItemDO::getProductId, productId) > 0
                || purchaseOrderItemMapper.selectCount(ErpPurchaseOrderItemDO::getProductId, productId) > 0
                || purchaseInItemMapper.selectCount(ErpPurchaseInItemDO::getProductId, productId) > 0
                || purchaseReturnItemMapper.selectCount(ErpPurchaseReturnItemDO::getProductId, productId) > 0
                || saleOrderItemMapper.selectCount(ErpSaleOrderItemDO::getProductId, productId) > 0
                || saleOutItemMapper.selectCount(ErpSaleOutItemDO::getProductId, productId) > 0
                || saleReturnItemMapper.selectCount(ErpSaleReturnItemDO::getProductId, productId) > 0;
    }

    private boolean hasWarehouseBusinessReference(Long warehouseId) {
        return stockRecordMapper.selectCount(ErpStockRecordDO::getWarehouseId, warehouseId) > 0
                || stockInItemMapper.selectCount(ErpStockInItemDO::getWarehouseId, warehouseId) > 0
                || stockOutItemMapper.selectCount(ErpStockOutItemDO::getWarehouseId, warehouseId) > 0
                || stockMoveItemMapper.selectCount(ErpStockMoveItemDO::getFromWarehouseId, warehouseId) > 0
                || stockMoveItemMapper.selectCount(ErpStockMoveItemDO::getToWarehouseId, warehouseId) > 0
                || stockCheckItemMapper.selectCount(ErpStockCheckItemDO::getWarehouseId, warehouseId) > 0
                || purchaseInItemMapper.selectCount(ErpPurchaseInItemDO::getWarehouseId, warehouseId) > 0
                || purchaseReturnItemMapper.selectCount(ErpPurchaseReturnItemDO::getWarehouseId, warehouseId) > 0
                || saleOutItemMapper.selectCount(ErpSaleOutItemDO::getWarehouseId, warehouseId) > 0
                || saleReturnItemMapper.selectCount(ErpSaleReturnItemDO::getWarehouseId, warehouseId) > 0;
    }

}
