package cn.iocoder.yudao.module.promotion.service.product;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.promotion.dal.dataobject.bargain.BargainActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.combination.CombinationActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.discount.DiscountProductDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.point.PointActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.reward.RewardActivityDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.bargain.BargainActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.combination.CombinationActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.discount.DiscountProductMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.point.PointActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.reward.RewardActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillActivityMapper;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionProductScopeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.PRODUCT_DELETE_FAIL_ACTIVE_PROMOTION;

/**
 * 商品删除前的营销引用校验。
 */
@Service
public class PromotionProductReferenceService {

    @Resource
    private SeckillActivityMapper seckillActivityMapper;
    @Resource
    private CombinationActivityMapper combinationActivityMapper;
    @Resource
    private BargainActivityMapper bargainActivityMapper;
    @Resource
    private PointActivityMapper pointActivityMapper;
    @Resource
    private DiscountProductMapper discountProductMapper;
    @Resource
    private RewardActivityMapper rewardActivityMapper;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;

    public void validateSpuDeletable(Long spuId) {
        List<String> references = new ArrayList<>();
        Integer enabled = CommonStatusEnum.ENABLE.getStatus();
        if (seckillActivityMapper.selectCount(new LambdaQueryWrapperX<SeckillActivityDO>()
                .eq(SeckillActivityDO::getSpuId, spuId).eq(SeckillActivityDO::getStatus, enabled)) > 0) {
            references.add("秒杀");
        }
        if (combinationActivityMapper.selectCount(new LambdaQueryWrapperX<CombinationActivityDO>()
                .eq(CombinationActivityDO::getSpuId, spuId).eq(CombinationActivityDO::getStatus, enabled)) > 0) {
            references.add("拼团");
        }
        if (bargainActivityMapper.selectCount(new LambdaQueryWrapperX<BargainActivityDO>()
                .eq(BargainActivityDO::getSpuId, spuId).eq(BargainActivityDO::getStatus, enabled)) > 0) {
            references.add("砍价");
        }
        if (pointActivityMapper.selectCount(new LambdaQueryWrapperX<PointActivityDO>()
                .eq(PointActivityDO::getSpuId, spuId).eq(PointActivityDO::getStatus, enabled)) > 0) {
            references.add("积分商城");
        }
        if (discountProductMapper.selectCount(new LambdaQueryWrapperX<DiscountProductDO>()
                .eq(DiscountProductDO::getSpuId, spuId).eq(DiscountProductDO::getActivityStatus, enabled)) > 0) {
            references.add("限时折扣");
        }
        if (hasEnabledRewardReference(spuId, enabled)) {
            references.add("满减送");
        }
        if (hasEnabledCouponReference(spuId, enabled)) {
            references.add("优惠券");
        }
        if (CollUtil.isNotEmpty(references)) {
            throw exception(PRODUCT_DELETE_FAIL_ACTIVE_PROMOTION, String.join("、", references));
        }
    }

    private boolean hasEnabledRewardReference(Long spuId, Integer enabled) {
        return rewardActivityMapper.selectCount(new LambdaQueryWrapperX<RewardActivityDO>()
                .eq(RewardActivityDO::getStatus, enabled)
                .eq(RewardActivityDO::getProductScope, PromotionProductScopeEnum.SPU.getScope())
                .apply("FIND_IN_SET({0}, product_scope_values)", spuId)) > 0;
    }

    private boolean hasEnabledCouponReference(Long spuId, Integer enabled) {
        return couponTemplateMapper.selectCount(new LambdaQueryWrapperX<CouponTemplateDO>()
                .eq(CouponTemplateDO::getStatus, enabled)
                .eq(CouponTemplateDO::getProductScope, PromotionProductScopeEnum.SPU.getScope())
                .apply("FIND_IN_SET({0}, product_scope_values)", spuId)) > 0;
    }

}
