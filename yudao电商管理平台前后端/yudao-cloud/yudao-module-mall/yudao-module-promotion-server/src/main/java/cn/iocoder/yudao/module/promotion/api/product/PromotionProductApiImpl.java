package cn.iocoder.yudao.module.promotion.api.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.promotion.service.product.PromotionProductReferenceService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class PromotionProductApiImpl implements PromotionProductApi {

    @Resource
    private PromotionProductReferenceService productReferenceService;

    @Override
    public CommonResult<Boolean> validateSpuDeletable(Long spuId) {
        productReferenceService.validateSpuDeletable(spuId);
        return success(true);
    }

}
