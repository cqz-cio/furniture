package cn.iocoder.yudao.module.promotion.service.product;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.promotion.dal.mysql.bargain.BargainActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.combination.CombinationActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.discount.DiscountProductMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.point.PointActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.reward.RewardActivityMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.seckill.seckillactivity.SeckillActivityMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.PRODUCT_DELETE_FAIL_ACTIVE_PROMOTION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PromotionProductReferenceServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PromotionProductReferenceService service;
    @Mock
    private SeckillActivityMapper seckillActivityMapper;
    @Mock
    private CombinationActivityMapper combinationActivityMapper;
    @Mock
    private BargainActivityMapper bargainActivityMapper;
    @Mock
    private PointActivityMapper pointActivityMapper;
    @Mock
    private DiscountProductMapper discountProductMapper;
    @Mock
    private RewardActivityMapper rewardActivityMapper;
    @Mock
    private CouponTemplateMapper couponTemplateMapper;

    @Test
    @SuppressWarnings("unchecked")
    void validateSpuDeletableRejectsActivePromotion() {
        when(seckillActivityMapper.selectCount(any(LambdaQueryWrapperX.class))).thenReturn(1L);

        assertServiceException(() -> service.validateSpuDeletable(88L),
                PRODUCT_DELETE_FAIL_ACTIVE_PROMOTION, "秒杀");
    }

    @Test
    void validateSpuDeletableAllowsProductWithoutActivePromotion() {
        assertDoesNotThrow(() -> service.validateSpuDeletable(88L));
    }

}
