package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductNavigationCategoryCreateReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCategoryServiceImplNavigationTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductCategoryServiceImpl productCategoryService;

    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private MallErpProductApi mallErpProductApi;

    @Test
    void navigationCountsOnlyProductsVisibleOnTheWebsite() {
        when(productCategoryMapper.selectListByStatus(ENABLE.getStatus())).thenReturn(List.of(
                category(1L, 0L, "Furniture", 30),
                category(10L, 1L, "Dining Tables", 20),
                category(11L, 1L, "Dining Chairs", 10)));
        when(productSpuMapper.selectListByCategoryIdsAndStatus(any(),
                eq(ProductSpuStatusEnum.ENABLE.getStatus()))).thenReturn(List.of(
                spu(100L, 10L),
                spu(101L, 10L),
                spu(102L, 11L)));
        when(productSkuService.getSkuListBySpuId(org.mockito.ArgumentMatchers.<Collection<Long>>any()))
                .thenReturn(List.of(
                        sku(1000L, 100L),
                        sku(1001L, 100L),
                        sku(1002L, 101L),
                        sku(1003L, 102L)));
        when(mallErpProductApi.getMappedMallSkuIds(any()))
                .thenReturn(CommonResult.success(Set.of(1000L, 1001L, 1003L)));

        List<ProductCategoryNavigationRespDTO> result =
                productCategoryService.getNavigationCategoryList();

        assertEquals(List.of("Dining Tables", "Dining Chairs"),
                result.stream().map(ProductCategoryNavigationRespDTO::getName).toList());
        assertEquals(List.of(1L, 1L),
                result.stream().map(ProductCategoryNavigationRespDTO::getPublishedProductCount).toList());
    }

    @Test
    void updateNavigationCategoryNameUpdatesOnlyTrimmedName() {
        when(productCategoryMapper.selectById(10L)).thenReturn(
                category(10L, 1L, "Dining Tables", 20));

        productCategoryService.updateNavigationCategoryName(10L, "  Contract Tables  ");

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(productCategoryMapper).updateById(captor.capture());
        assertEquals(10L, captor.getValue().getId());
        assertEquals("Contract Tables", captor.getValue().getName());
        assertNull(captor.getValue().getParentId());
        assertNull(captor.getValue().getStatus());
    }

    @Test
    void createNavigationCategoryAllowsBlankImageThroughMethodValidation() {
        when(productCategoryMapper.selectById(1L)).thenReturn(
                category(1L, 0L, "VANZ Furniture", 0));
        doAnswer(invocation -> {
            ProductCategoryDO inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        }).when(productCategoryMapper).insert(any(ProductCategoryDO.class));

        ProductNavigationCategoryCreateReqVO request = new ProductNavigationCategoryCreateReqVO()
                .setParentId(1L)
                .setName("  Contract Furniture  ");
        ProductCategorySaveReqVO genericRequest = new ProductCategorySaveReqVO()
                .setParentId(1L)
                .setName("Contract Furniture")
                .setPicUrl("")
                .setSort(0)
                .setStatus(ENABLE.getStatus());

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ProductCategoryService validatedService = validatedService(factory.getValidator());
            assertThrows(ConstraintViolationException.class,
                    () -> validatedService.createCategory(genericRequest));
            assertEquals(88L, validatedService.createNavigationCategory(request));
        }

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(productCategoryMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getParentId());
        assertEquals("Contract Furniture", captor.getValue().getName());
        assertEquals("", captor.getValue().getPicUrl());
        assertEquals(0, captor.getValue().getSort());
        assertEquals(ENABLE.getStatus(), captor.getValue().getStatus());
    }

    private ProductCategoryService validatedService(Validator validator) {
        ProxyFactory proxyFactory = new ProxyFactory(productCategoryService);
        proxyFactory.setInterfaces(ProductCategoryService.class);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validator));
        return (ProductCategoryService) proxyFactory.getProxy();
    }

    private static ProductCategoryDO category(Long id, Long parentId, String name, Integer sort) {
        return new ProductCategoryDO().setId(id).setParentId(parentId).setName(name)
                .setSort(sort).setStatus(ENABLE.getStatus());
    }

    private static ProductSpuDO spu(Long id, Long categoryId) {
        return new ProductSpuDO().setId(id).setCategoryId(categoryId)
                .setStatus(ProductSpuStatusEnum.ENABLE.getStatus());
    }

    private static ProductSkuDO sku(Long id, Long spuId) {
        return new ProductSkuDO().setId(id).setSpuId(spuId);
    }

}
