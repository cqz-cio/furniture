package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductDetailConfigSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.service.spu.ProductWebsiteFieldPolicyService.ProductWebsiteFieldPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_SAVE_FAIL_DELIVERY_TYPES_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductAdminFieldPolicyServiceTest {

    private final ProductWebsiteFieldPolicyService websiteFieldPolicyService =
            mock(ProductWebsiteFieldPolicyService.class);
    private final ProductAdminFieldPolicyService service =
            new ProductAdminFieldPolicyService(websiteFieldPolicyService);

    @Test
    void shouldAllowEmptyDeliveryTypesForB2B() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(true, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO().setRoomCategoryId(30L);

        service.prepareForSave(saveReqVO);

        assertEquals(List.of(), saveReqVO.getDeliveryTypes());
    }

    @Test
    void shouldPreserveExistingDeliveryTypesForB2B() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(true, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO()
                .setRoomCategoryId(30L).setDeliveryTypes(List.of(1));

        service.prepareForSave(saveReqVO);

        assertEquals(List.of(1), saveReqVO.getDeliveryTypes());
    }

    @Test
    void shouldPreserveBlankFinishForB2B() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(true, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO()
                .setRoomCategoryId(30L)
                .setDetailConfig(new ProductDetailConfigSaveReqVO().setFinish("   "));

        service.prepareForSave(saveReqVO);

        assertEquals("", saveReqVO.getDetailConfig().getFinish());
    }

    @Test
    void shouldPreserveExplicitFinishForB2B() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(true, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO()
                .setRoomCategoryId(30L)
                .setDetailConfig(new ProductDetailConfigSaveReqVO()
                        .setFinish("  Whitewashed finishing  "));

        service.prepareForSave(saveReqVO);

        assertEquals("Whitewashed finishing", saveReqVO.getDetailConfig().getFinish());
    }

    @Test
    void shouldRequireRoomCategoryForB2B() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(true, Set.of()));

        assertThrows(ServiceException.class,
                () -> service.prepareForSave(new ProductSpuSaveReqVO()));
    }

    @Test
    void shouldRequireDeliveryTypesForB2C() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(false, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO().setDeliveryTypes(List.of());

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.prepareForSave(saveReqVO));

        assertEquals(SPU_SAVE_FAIL_DELIVERY_TYPES_EMPTY.getCode(), exception.getCode());
    }

    @Test
    void shouldNotAddFinishForB2C() {
        when(websiteFieldPolicyService.getCurrentPolicy())
                .thenReturn(new ProductWebsiteFieldPolicy(false, Set.of()));
        ProductSpuSaveReqVO saveReqVO = new ProductSpuSaveReqVO().setDeliveryTypes(List.of(1));

        service.prepareForSave(saveReqVO);

        assertEquals(null, saveReqVO.getDetailConfig());
    }

}
