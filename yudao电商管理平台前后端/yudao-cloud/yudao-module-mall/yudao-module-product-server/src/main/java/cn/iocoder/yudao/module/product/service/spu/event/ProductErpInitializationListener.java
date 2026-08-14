package cn.iocoder.yudao.module.product.service.spu.event;

import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Web 商品事务提交后，为全部新 SKU 初始化一次 ERP 基础商品和映射。
 */
@Component
@Slf4j
public class ProductErpInitializationListener {

    @Resource
    private ProductSkuService productSkuService;
    @Resource
    @Lazy // ERP 服务会反向读取商品 API，延迟注入避免单体部署循环依赖
    private MallErpProductApi mallErpProductApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void initializeErpProducts(ProductSpuCreatedEvent event) {
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(event.spuId());
        if (skus == null || skus.isEmpty()) {
            log.error("ERP initialization skipped because Web SPU {} has no committed SKU", event.spuId());
            return;
        }
        for (ProductSkuDO sku : skus) {
            try {
                MallErpProductDTO result = mallErpProductApi
                        .initializeMallSku(event.spuId(), sku.getId()).getCheckedData();
                if (result == null || !"SUCCESS".equals(result.getSyncStatus())) {
                    log.error("ERP initialization failed for Web SPU {}, SKU {}: {}", event.spuId(),
                            sku.getId(), result == null ? "empty ERP response" : result.getLastError());
                }
            } catch (Exception exception) {
                // Web 商品已经提交，单个 ERP 初始化失败不能伪装成 Web 创建失败；保留未映射状态供补建。
                log.error("ERP initialization request failed for Web SPU {}, SKU {}",
                        event.spuId(), sku.getId(), exception);
            }
        }
    }

}
