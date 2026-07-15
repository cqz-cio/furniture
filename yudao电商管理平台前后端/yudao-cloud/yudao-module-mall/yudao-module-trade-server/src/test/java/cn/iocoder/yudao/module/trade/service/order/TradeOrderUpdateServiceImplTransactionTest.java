package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.product.api.comment.ProductCommentApi;
import cn.iocoder.yudao.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCommentCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryPickUpStoreService;
import cn.iocoder.yudao.module.trade.service.message.TradeMessageService;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(TradeOrderUpdateServiceImpl.class)
public class TradeOrderUpdateServiceImplTransactionTest extends BaseDbUnitTest {

    @Resource
    private TradeOrderUpdateServiceImpl tradeOrderUpdateService;

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private DataSource dataSource;

    @MockBean
    private TradeNoRedisDAO tradeNoRedisDAO;
    @MockBean
    private CartService cartService;
    @MockBean
    private TradePriceService tradePriceService;
    @MockBean
    private DeliveryExpressService deliveryExpressService;
    @MockBean
    private TradeMessageService tradeMessageService;
    @MockBean
    private DeliveryPickUpStoreService pickUpStoreService;
    @MockBean
    private PayOrderApi payOrderApi;
    @MockBean
    private MemberAddressApi addressApi;
    @MockBean
    private ProductCommentApi productCommentApi;
    @MockBean
    private SocialClientApi socialClientApi;
    @MockBean
    private PayRefundApi payRefundApi;
    @MockBean
    private CombinationRecordApi combinationRecordApi;
    @MockBean
    private TradeOrderProperties tradeOrderProperties;
    @MockBean
    private TradeOrderHandler tradeOrderHandler;

    @Test
    public void testCreateOrderCommentsByMember_rollsBackLocalDbStateWhenBatchCommentFails() {
        Long userId = 10L;
        ensureTradeOrderColumns();
        TradeOrderDO order = createCompletedOrder(userId);
        tradeOrderMapper.insert(order);
        TradeOrderItemDO firstItem = createOrderItem(order.getId(), userId, 301L, "Desk Chair");
        TradeOrderItemDO secondItem = createOrderItem(order.getId(), userId, 302L, "Office Lamp");
        tradeOrderItemMapper.insert(firstItem);
        tradeOrderItemMapper.insert(secondItem);
        when(productCommentApi.createComments(any()))
                .thenThrow(new ServiceException(500, "comment create failed"));

        assertThrows(ServiceException.class, () -> tradeOrderUpdateService.createOrderCommentsByMember(userId,
                createBatchCommentReqVO(order.getId(), firstItem.getId(), secondItem.getId())));

        TradeOrderDO dbOrder = tradeOrderMapper.selectById(order.getId());
        TradeOrderItemDO dbFirstItem = tradeOrderItemMapper.selectById(firstItem.getId());
        TradeOrderItemDO dbSecondItem = tradeOrderItemMapper.selectById(secondItem.getId());
        assertFalse(dbOrder.getCommentStatus());
        assertNull(dbOrder.getFinishTime());
        assertFalse(dbFirstItem.getCommentStatus());
        assertFalse(dbSecondItem.getCommentStatus());
    }

    private void ensureTradeOrderColumns() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS give_coupon_template_counts VARCHAR(2000)");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS give_coupon_ids VARCHAR(2000)");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS seckill_activity_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS bargain_activity_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS bargain_record_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS combination_activity_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS combination_head_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS combination_record_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN IF NOT EXISTS point_activity_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order_item ADD COLUMN IF NOT EXISTS registry_id BIGINT");
        jdbcTemplate.execute("ALTER TABLE trade_order_item ADD COLUMN IF NOT EXISTS registry_item_id BIGINT");
    }

    private static TradeOrderDO createCompletedOrder(Long userId) {
        return TradeOrderDO.builder()
                .no("T202607070001")
                .type(TradeOrderTypeEnum.NORMAL.getType())
                .terminal(TerminalEnum.H5.getTerminal())
                .userId(userId)
                .userIp("127.0.0.1")
                .status(TradeOrderStatusEnum.COMPLETED.getStatus())
                .productCount(2)
                .commentStatus(Boolean.FALSE)
                .payStatus(Boolean.TRUE)
                .totalPrice(2000)
                .discountPrice(0)
                .deliveryPrice(0)
                .adjustPrice(0)
                .payPrice(2000)
                .deliveryType(DeliveryTypeEnum.EXPRESS.getType())
                .logisticsId(TradeOrderDO.LOGISTICS_ID_NULL)
                .logisticsNo("")
                .refundStatus(TradeOrderRefundStatusEnum.NONE.getStatus())
                .refundPrice(0)
                .couponId(0L)
                .couponPrice(0)
                .usePoint(0)
                .pointPrice(0)
                .givePoint(0)
                .refundPoint(0)
                .vipPrice(0)
                .receiverName("Jane Buyer")
                .receiverMobile("15500001111")
                .receiverAreaId(1)
                .receiverDetailAddress("100 Main St")
                .build();
    }

    private static TradeOrderItemDO createOrderItem(Long orderId, Long userId, Long skuId, String spuName) {
        TradeOrderItemDO item = new TradeOrderItemDO();
        item.setOrderId(orderId);
        item.setUserId(userId);
        item.setSpuId(skuId + 1000);
        item.setSpuName(spuName);
        item.setSkuId(skuId);
        item.setPicUrl("https://example.com/item.png");
        item.setCount(1);
        item.setCommentStatus(Boolean.FALSE);
        item.setPrice(1000);
        item.setDiscountPrice(0);
        item.setDeliveryPrice(0);
        item.setAdjustPrice(0);
        item.setPayPrice(1000);
        item.setCouponPrice(0);
        item.setPointPrice(0);
        item.setUsePoint(0);
        item.setGivePoint(0);
        item.setVipPrice(0);
        item.setAfterSaleStatus(0);
        return item;
    }

    private static AppTradeOrderCommentCreateReqVO createBatchCommentReqVO(Long orderId, Long... orderItemIds) {
        AppTradeOrderCommentCreateReqVO reqVO = new AppTradeOrderCommentCreateReqVO();
        reqVO.setOrderId(orderId);
        reqVO.setAnonymous(Boolean.FALSE);
        reqVO.setItems(Arrays.stream(orderItemIds)
                .map(orderItemId -> new AppTradeOrderCommentCreateReqVO.Item()
                        .setOrderItemId(orderItemId)
                        .setDescriptionScores(5)
                        .setBenefitScores(5)
                        .setContent("鍟嗗搧婊℃剰")
                        .setPicUrls(Collections.emptyList()))
                .collect(Collectors.toList()));
        return reqVO;
    }

}
