package cn.iocoder.yudao.module.erp.service.stock;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.yudao.module.erp.service.common.ErpReferenceValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpWarehouseServiceImplDeleteTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ErpWarehouseServiceImpl service;
    @Mock
    private ErpWarehouseMapper warehouseMapper;
    @Mock
    private ErpReferenceValidationService referenceValidationService;

    @Test
    void deleteWarehouseStopsWhenReferenceValidationFails() {
        when(warehouseMapper.selectById(31L)).thenReturn(new ErpWarehouseDO().setId(31L));
        org.mockito.Mockito.doThrow(new IllegalStateException("referenced"))
                .when(referenceValidationService).validateWarehouseDeletable(31L);

        assertThrows(IllegalStateException.class, () -> service.deleteWarehouse(31L));

        verify(warehouseMapper, never()).deleteById(31L);
    }

}
