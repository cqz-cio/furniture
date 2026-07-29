package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuDetailRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_ENABLE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;

@Tag(name = "用户 APP - 商品 SPU")
@RestController
@RequestMapping("/product/spu")
@Validated
public class AppProductSpuController {

    @Resource
    private ProductSpuService productSpuService;
    @Resource
    private ProductSkuService productSkuService;
    @Resource
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Resource
    private ProductCategoryService productCategoryService;
    @Resource
    private MallErpProductApi mallErpProductApi;

    @GetMapping("/list-by-ids")
    @Operation(summary = "获得商品 SPU 列表")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PermitAll
    public CommonResult<List<AppProductSpuRespVO>> getSpuList(@RequestParam("ids") Set<Long> ids) {
        List<ProductSpuDO> list = productSpuService.getSpuList(ids);
        list = filterErpAlignedSpus(list);
        if (CollUtil.isEmpty(list)) {
            return success(Collections.emptyList());
        }
        overlayErpStock(list);

        // 拼接返回
        list.forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        List<AppProductSpuRespVO> voList = BeanUtils.toBean(list, AppProductSpuRespVO.class);
        overlayCategoryNames(voList);
        return success(voList);
    }

    @GetMapping("/page")
    @Operation(summary = "获得商品 SPU 分页")
    @PermitAll
    public CommonResult<PageResult<AppProductSpuRespVO>> getSpuPage(@Valid AppProductSpuPageReqVO pageVO) {
        int requestedPageNo = pageVO.getPageNo();
        int requestedPageSize = pageVO.getPageSize();
        pageVO.setPageNo(1);
        pageVO.setPageSize(cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE);
        PageResult<ProductSpuDO> unfilteredPage = productSpuService.getSpuPage(pageVO);
        pageVO.setPageNo(requestedPageNo);
        pageVO.setPageSize(requestedPageSize);
        List<ProductSpuDO> aligned = filterErpAlignedSpus(unfilteredPage.getList());
        int fromIndex = Math.min((requestedPageNo - 1) * requestedPageSize, aligned.size());
        int toIndex = Math.min(fromIndex + requestedPageSize, aligned.size());
        PageResult<ProductSpuDO> pageResult = new PageResult<>(
                new ArrayList<>(aligned.subList(fromIndex, toIndex)), (long) aligned.size());
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }
        overlayErpStock(pageResult.getList());

        // 拼接返回
        pageResult.getList().forEach(spu -> spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount()));
        PageResult<AppProductSpuRespVO> voPageResult = BeanUtils.toBean(pageResult, AppProductSpuRespVO.class);
        overlayCategoryNames(voPageResult.getList());
        return success(voPageResult);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得商品 SPU 明细")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<AppProductSpuDetailRespVO> getSpuDetail(@RequestParam("id") Long id) {
        // 获得商品 SPU
        ProductSpuDO spu = productSpuService.getSpu(id);
        if (spu == null) {
            throw exception(SPU_NOT_EXISTS);
        }
        if (!ProductSpuStatusEnum.isEnable(spu.getStatus())) {
            throw exception(SPU_NOT_ENABLE, spu.getName());
        }
        // 获得商品 SKU
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(spu.getId());
        if (!isErpAligned(skus, getMappedSkuIds(skus))) {
            throw exception(SPU_NOT_EXISTS);
        }
        overlayErpStock(spu, skus);

        // 增加浏览量
        productSpuService.updateBrowseCount(id, 1);
        // 保存浏览记录
        productBrowseHistoryService.createBrowseHistory(getLoginUserId(), id);

        // 拼接返回
        spu.setSalesCount(spu.getSalesCount() + spu.getVirtualSalesCount());
        AppProductSpuDetailRespVO spuVO = BeanUtils.toBean(spu, AppProductSpuDetailRespVO.class)
                .setSkus(toPublicSkuVOs(skus));
        ProductCategoryDO category = productCategoryService.getCategory(spu.getCategoryId());
        spuVO.setCategoryName(category == null ? null : category.getName());
        return success(spuVO);
    }

    private List<AppProductSpuDetailRespVO.Sku> toPublicSkuVOs(List<ProductSkuDO> skus) {
        List<AppProductSpuDetailRespVO.Sku> skuVOs =
                BeanUtils.toBean(skus, AppProductSpuDetailRespVO.Sku.class);
        for (int i = 0; i < skus.size(); i++) {
            MallErpProductDTO erpProduct = mallErpProductApi.getByMallSkuId(skus.get(i).getId()).getCheckedData();
            if (erpProduct != null) {
                skuVOs.get(i).setSkuCode(erpProduct.getErpProductCode());
            }
        }
        return skuVOs;
    }

    private void overlayCategoryNames(List<AppProductSpuRespVO> spus) {
        if (CollUtil.isEmpty(spus)) {
            return;
        }
        Map<Long, String> categoryNames = productCategoryService.getEnableCategoryList(
                        spus.stream().map(AppProductSpuRespVO::getCategoryId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId, ProductCategoryDO::getName));
        spus.forEach(spu -> spu.setCategoryName(categoryNames.get(spu.getCategoryId())));
    }

    private void overlayErpStock(List<ProductSpuDO> spus) {
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(
                spus.stream().map(ProductSpuDO::getId).collect(Collectors.toSet()));
        spus.forEach(spu -> overlayErpStock(spu, skus.stream()
                .filter(sku -> spu.getId().equals(sku.getSpuId())).collect(Collectors.toList())));
    }

    private List<ProductSpuDO> filterErpAlignedSpus(List<ProductSpuDO> spus) {
        if (CollUtil.isEmpty(spus)) {
            return Collections.emptyList();
        }
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(
                spus.stream().map(ProductSpuDO::getId).collect(Collectors.toSet()));
        Map<Long, List<ProductSkuDO>> skusBySpuId = skus.stream()
                .collect(Collectors.groupingBy(ProductSkuDO::getSpuId));
        Set<Long> mappedSkuIds = getMappedSkuIds(skus);
        return spus.stream().filter(spu -> isErpAligned(skusBySpuId.get(spu.getId()), mappedSkuIds))
                .collect(Collectors.toList());
    }

    private Set<Long> getMappedSkuIds(Collection<ProductSkuDO> skus) {
        if (CollUtil.isEmpty(skus)) {
            return Collections.emptySet();
        }
        return mallErpProductApi.getMappedMallSkuIds(
                skus.stream().map(ProductSkuDO::getId).collect(Collectors.toSet())).getCheckedData();
    }

    private boolean isErpAligned(List<ProductSkuDO> skus, Set<Long> mappedSkuIds) {
        return CollUtil.isNotEmpty(skus) && skus.stream().allMatch(sku -> mappedSkuIds.contains(sku.getId()));
    }

    private void overlayErpStock(ProductSpuDO spu, List<ProductSkuDO> skus) {
        skus.forEach(sku -> sku.setStock(mallErpProductApi.getSellableStock(sku.getId())
                .getCheckedData().getSellableStock().intValue()));
        spu.setStock(skus.stream().mapToInt(ProductSkuDO::getStock).sum());
    }

}
