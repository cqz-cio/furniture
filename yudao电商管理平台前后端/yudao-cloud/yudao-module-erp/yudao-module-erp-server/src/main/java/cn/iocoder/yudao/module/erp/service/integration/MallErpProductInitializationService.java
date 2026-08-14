package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;

/**
 * Web 商品首次进入 ERP 时的基础商品初始化服务。
 */
public interface MallErpProductInitializationService {

    /**
     * 为 Web SKU 创建或复用 ERP 基础商品，并建立一对一映射。
     *
     * 重复调用不会修改已存在的 ERP 商品资料。
     */
    MallErpProductDTO initializeMallSku(Long mallSpuId, Long mallSkuId);

}
