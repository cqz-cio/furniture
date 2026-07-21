package cn.iocoder.yudao.module.product.api.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 商品 SPU API 接口实现类
 *
 * @author LeeYan9
 * @since 2022-09-06
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class ProductSpuApiImpl implements ProductSpuApi {

    @Resource
    private ProductSpuService spuService;
    @Resource
    private ProductCategoryService categoryService;

    @Override
    public CommonResult<List<ProductSpuRespDTO>> getSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.getSpuList(ids);
        return success(toDtos(spus));
    }

    @Override
    public CommonResult<List<ProductSpuRespDTO>> validateSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.validateSpuList(ids);
        return success(toDtos(spus));
    }

    @Override
    public CommonResult<ProductSpuRespDTO> getSpu(Long id) {
        ProductSpuDO spu = spuService.getSpu(id);
        if (spu == null) {
            return success(null);
        }
        ProductSpuRespDTO dto = BeanUtils.toBean(spu, ProductSpuRespDTO.class);
        ProductCategoryDO category = categoryService.getCategory(spu.getCategoryId());
        dto.setCategoryName(category == null ? null : category.getName());
        return success(dto);
    }

    private List<ProductSpuRespDTO> toDtos(List<ProductSpuDO> spus) {
        List<ProductSpuRespDTO> dtos = BeanUtils.toBean(spus, ProductSpuRespDTO.class);
        Map<Long, String> categoryNames = categoryService.getEnableCategoryList(
                        spus.stream().map(ProductSpuDO::getCategoryId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(ProductCategoryDO::getId, ProductCategoryDO::getName));
        dtos.forEach(dto -> dto.setCategoryName(categoryNames.get(dto.getCategoryId())));
        return dtos;
    }

}
