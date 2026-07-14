package cn.iocoder.yudao.module.erp.api.integration;

import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MallErpProductApiContractTest {

    @Test
    void exposesMallProductAndStockContract() throws Exception {
        Method syncOne = MallErpProductApi.class.getMethod("syncMallSku", Long.class, Long.class);
        Method syncAll = MallErpProductApi.class.getMethod("syncAllMallSkus");
        Method getOne = MallErpProductApi.class.getMethod("getByMallSkuId", Long.class);
        Method getStock = MallErpProductApi.class.getMethod("getSellableStock", Long.class);
        Method validate = MallErpProductApi.class.getMethod("validateSellableStock", List.class);

        assertEquals(CommonResult.class, syncOne.getReturnType());
        assertEquals(CommonResult.class, syncAll.getReturnType());
        assertEquals(CommonResult.class, getOne.getReturnType());
        assertEquals(CommonResult.class, getStock.getReturnType());
        assertEquals(CommonResult.class, validate.getReturnType());

        MallErpProductDTO product = new MallErpProductDTO()
                .setErpProductId(9L).setErpProductCode("RH-121-26")
                .setBaseName("Walnut Four-Door Sideboard")
                .setCostPrice(new BigDecimal("1700.00"))
                .setEnabled(true).setSellableStock(new BigDecimal("12"));
        assertEquals(9L, product.getErpProductId());
        assertEquals("RH-121-26", product.getErpProductCode());
        assertEquals(new BigDecimal("12"), product.getSellableStock());

        MallErpStockRequestDTO request = new MallErpStockRequestDTO().setMallSkuId(26L).setCount(new BigDecimal("2"));
        assertEquals(26L, request.getMallSkuId());
    }
}
