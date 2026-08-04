package cn.iocoder.yudao.module.product.service.category;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.CATEGORY_LEVEL;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.PARENT_ID_NULL;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/**
 * 商品分类 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ProductCategoryServiceImpl implements ProductCategoryService {

    @Resource
    private ProductCategoryMapper productCategoryMapper;
    @Resource
    private ProductSpuMapper productSpuMapper;
    @Resource
    private ProductSkuService productSkuService;
    @Resource
    @Lazy // 单体部署下 ERP 映射服务会反向依赖商品 API，延迟解析避免启动期循环依赖
    private MallErpProductApi mallErpProductApi;
    @Resource
    @Lazy // 循环依赖，避免报错
    private ProductSpuService productSpuService;

    @Override
    public Long createCategory(ProductCategorySaveReqVO createReqVO) {
        // 校验父分类存在
        validateParentProductCategory(createReqVO.getParentId());

        // 插入
        ProductCategoryDO category = BeanUtils.toBean(createReqVO, ProductCategoryDO.class);
        productCategoryMapper.insert(category);
        // 返回
        return category.getId();
    }

    @Override
    public void updateCategory(ProductCategorySaveReqVO updateReqVO) {
        // 校验分类是否存在
        validateProductCategoryExists(updateReqVO.getId());
        // 校验父分类存在
        validateParentProductCategory(updateReqVO.getParentId());

        // 更新
        ProductCategoryDO updateObj = BeanUtils.toBean(updateReqVO, ProductCategoryDO.class);
        productCategoryMapper.updateById(updateObj);
    }

    @Override
    public void deleteCategory(Long id) {
        // 校验分类是否存在
        validateProductCategoryExists(id);
        // 校验是否还有子分类
        if (productCategoryMapper.selectCountByParentId(id) > 0) {
            throw exception(CATEGORY_EXISTS_CHILDREN);
        }
        // 校验分类是否绑定了 SPU
        Long spuCount = productSpuService.getSpuCountByCategoryId(id);
        if (spuCount > 0) {
            throw exception(CATEGORY_HAVE_BIND_SPU);
        }
        // 删除
        productCategoryMapper.deleteById(id);
    }

    private void validateParentProductCategory(Long id) {
        // 如果是根分类，无需验证
        if (Objects.equals(id, PARENT_ID_NULL)) {
            return;
        }
        // 父分类不存在
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_PARENT_NOT_EXISTS);
        }
        // 父分类不能是二级分类
        if (!Objects.equals(category.getParentId(), PARENT_ID_NULL)) {
            throw exception(CATEGORY_PARENT_NOT_FIRST_LEVEL);
        }
    }

    private void validateProductCategoryExists(Long id) {
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
    }

    @Override
    public void validateCategoryList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得商品分类信息
        List<ProductCategoryDO> list = productCategoryMapper.selectByIds(ids);
        Map<Long, ProductCategoryDO> categoryMap = CollectionUtils.convertMap(list, ProductCategoryDO::getId);
        // 校验
        ids.forEach(id -> {
            // 校验分类是否存在
            ProductCategoryDO category = categoryMap.get(id);
            if (category == null) {
                throw exception(CATEGORY_NOT_EXISTS);
            }
            // 校验分类是否启用
            if (!CommonStatusEnum.ENABLE.getStatus().equals(category.getStatus())) {
                throw exception(CATEGORY_DISABLED, category.getName());
            }
            // 商品分类层级校验，必须使用第二级的商品分类
            if (getCategoryLevel(id) < CATEGORY_LEVEL) {
                throw exception(SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR);
            }
        });
    }

    @Override
    public ProductCategoryDO getCategory(Long id) {
        return productCategoryMapper.selectById(id);
    }

    @Override
    public void validateCategory(Long id) {
        ProductCategoryDO category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw exception(CATEGORY_NOT_EXISTS);
        }
        if (Objects.equals(category.getStatus(), CommonStatusEnum.DISABLE.getStatus())) {
            throw exception(CATEGORY_DISABLED, category.getName());
        }
    }

    @Override
    public Integer getCategoryLevel(Long id) {
        if (Objects.equals(id, PARENT_ID_NULL)) {
            return 0;
        }
        int level = 1;
        // for 的原因，是因为避免脏数据，导致可能的死循环。一般不会超过 100 层哈
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            // 如果没有父节点，break 结束
            ProductCategoryDO category = productCategoryMapper.selectById(id);
            if (category == null
                    || Objects.equals(category.getParentId(), PARENT_ID_NULL)) {
                break;
            }
            // 继续递归父节点
            level++;
            id = category.getParentId();
        }
        return level;
    }

    @Override
    public List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO listReqVO) {
        return productCategoryMapper.selectList(listReqVO);
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList() {
        return productCategoryMapper.selectListByStatus(CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public List<ProductCategoryDO> getEnableCategoryList(List<Long> ids) {
        return productCategoryMapper.selectListByIdAndStatus(ids, CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public List<ProductCategoryNavigationRespDTO> getNavigationCategoryList() {
        List<ProductCategoryDO> categories = getEnableCategoryList().stream()
                .filter(category -> !Objects.equals(category.getParentId(), PARENT_ID_NULL))
                .sorted(Comparator.comparing(ProductCategoryDO::getSort,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductCategoryDO::getId))
                .toList();
        if (categories.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> countMap = getWebsiteVisibleProductCountMap(
                CollectionUtils.convertSet(categories, ProductCategoryDO::getId));
        return categories.stream().map(category -> {
            ProductCategoryNavigationRespDTO response = BeanUtils.toBean(
                    category, ProductCategoryNavigationRespDTO.class);
            response.setPublishedProductCount(countMap.getOrDefault(category.getId(), 0L));
            return response;
        }).toList();
    }

    private Map<Long, Long> getWebsiteVisibleProductCountMap(Set<Long> categoryIds) {
        List<ProductSpuDO> spus = productSpuMapper.selectListByCategoryIdsAndStatus(
                categoryIds, ProductSpuStatusEnum.ENABLE.getStatus());
        if (CollUtil.isEmpty(spus)) {
            return Collections.emptyMap();
        }
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(
                CollectionUtils.convertSet(spus, ProductSpuDO::getId));
        if (CollUtil.isEmpty(skus)) {
            return Collections.emptyMap();
        }
        Map<Long, List<ProductSkuDO>> skusBySpuId = skus.stream()
                .collect(Collectors.groupingBy(ProductSkuDO::getSpuId));
        Set<Long> mappedSkuIds = mallErpProductApi.getMappedMallSkuIds(
                CollectionUtils.convertSet(skus, ProductSkuDO::getId)).getCheckedData();
        if (mappedSkuIds == null) {
            mappedSkuIds = Collections.emptySet();
        }
        Set<Long> finalMappedSkuIds = mappedSkuIds;
        return spus.stream()
                .filter(spu -> {
                    List<ProductSkuDO> spuSkus = skusBySpuId.get(spu.getId());
                    return CollUtil.isNotEmpty(spuSkus)
                            && spuSkus.stream().allMatch(sku -> finalMappedSkuIds.contains(sku.getId()));
                })
                .collect(Collectors.groupingBy(ProductSpuDO::getCategoryId, Collectors.counting()));
    }

}
