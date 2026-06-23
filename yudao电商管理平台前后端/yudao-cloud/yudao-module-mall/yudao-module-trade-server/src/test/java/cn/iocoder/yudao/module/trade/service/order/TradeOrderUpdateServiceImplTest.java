package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderUpdateAddressReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.cart.CartService;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.yudao.module.trade.service.price.TradePriceService;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_CREATE_FAIL_ADDRESS_VERIFICATION_MISMATCH;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_DELIVERY_FAIL_ADDRESS_VERIFICATION_NEEDS_REVIEW;

public class TradeOrderUpdateServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private TradeOrderUpdateServiceImpl tradeOrderUpdateService;

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Mock
    private TradeNoRedisDAO tradeNoRedisDAO;
    @Mock
    private CartService cartService;
    @Mock
    private TradePriceService tradePriceService;
    @Mock
    private MemberAddressApi addressApi;
    @Mock
    private PayOrderApi payOrderApi;
    @Mock
    private List<TradeOrderHandler> tradeOrderHandlers;

    @Test
    public void testCreateOrder_rejectsAddressVerificationForDifferentSelectedAddress() {
        Long userId = 10L;
        Long addressId = 200L;
        AppTradeOrderCreateReqVO reqVO = buildCreateOrderReqVO(addressId);
        reqVO.setAddressVerification(buildAddressVerification("999 Other St", "Austin", "TX", "78701"));
        MemberAddressRespDTO actualAddress = buildMemberAddress(addressId,
                "1600 Amphitheatre Pkwy, Mountain View, CA 94043");
        when(cartService.getCartList(eq(userId), any())).thenReturn(Collections.emptyList());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(buildPriceCalculateRespBO());
        when(addressApi.getAddress(addressId, userId)).thenReturn(CommonResult.success(actualAddress));
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX)).thenReturn("T202606170001");

        assertThrows(ServiceException.class, () -> tradeOrderUpdateService.createOrder(userId, reqVO));

        verify(tradeOrderMapper, never()).insert(org.mockito.ArgumentMatchers.<TradeOrderDO>any());
    }

    @Test
    public void testCreateOrder_rejectsAddressVerificationWhenActualAddressOmitsConfirmedStreet() {
        Long userId = 10L;
        Long addressId = 200L;
        AppTradeOrderCreateReqVO reqVO = buildCreateOrderReqVO(addressId);
        reqVO.setAddressVerification(buildAddressVerification());
        MemberAddressRespDTO actualAddress = buildMemberAddress(addressId, "Mountain View, CA 94043");
        when(cartService.getCartList(eq(userId), any())).thenReturn(Collections.emptyList());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(buildPriceCalculateRespBO());
        when(addressApi.getAddress(addressId, userId)).thenReturn(CommonResult.success(actualAddress));
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX)).thenReturn("T202606170001");

        assertThrows(ServiceException.class, () -> tradeOrderUpdateService.createOrder(userId, reqVO));

        verify(tradeOrderMapper, never()).insert(org.mockito.ArgumentMatchers.<TradeOrderDO>any());
    }

    @Test
    public void testCreateOrder_rejectsAddressVerificationWhenStateOnlyAppearsInsideAnotherWord() {
        Long userId = 10L;
        Long addressId = 200L;
        AppTradeOrderCreateReqVO reqVO = buildCreateOrderReqVO(addressId);
        reqVO.setAddressVerification(buildAddressVerification());
        MemberAddressRespDTO actualAddress = buildMemberAddress(addressId,
                "1600 Amphitheatre Pkwy Campus, Mountain View, 94043");
        when(cartService.getCartList(eq(userId), any())).thenReturn(Collections.emptyList());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(buildPriceCalculateRespBO());
        when(addressApi.getAddress(addressId, userId)).thenReturn(CommonResult.success(actualAddress));
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX)).thenReturn("T202606170001");

        assertThrows(ServiceException.class, () -> tradeOrderUpdateService.createOrder(userId, reqVO));

        verify(tradeOrderMapper, never()).insert(org.mockito.ArgumentMatchers.<TradeOrderDO>any());
    }

    @Test
    public void testCreateOrder_rejectsUnsupportedAddressVerificationValuesInServiceLayer() {
        Long userId = 10L;
        Long addressId = 200L;
        AppTradeOrderCreateReqVO reqVO = buildCreateOrderReqVO(addressId);
        Map<String, Object> addressVerification = buildAddressVerification();
        addressVerification.put("source", "trusted-because-user-said-so");
        addressVerification.put("addressSource", "legacy");
        addressVerification.put("status", "maybe");
        addressVerification.put("choice", "skip-review");
        reqVO.setAddressVerification(addressVerification);
        MemberAddressRespDTO actualAddress = buildMemberAddress(addressId,
                "1600 Amphitheatre Pkwy, Mountain View, CA 94043");
        when(cartService.getCartList(eq(userId), any())).thenReturn(Collections.emptyList());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(buildPriceCalculateRespBO());
        when(addressApi.getAddress(addressId, userId)).thenReturn(CommonResult.success(actualAddress));
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX)).thenReturn("T202606170001");

        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> tradeOrderUpdateService.createOrder(userId, reqVO));

        assertEquals(ORDER_CREATE_FAIL_ADDRESS_VERIFICATION_MISMATCH.getCode(), serviceException.getCode());
        verify(tradeOrderMapper, never()).insert(org.mockito.ArgumentMatchers.<TradeOrderDO>any());
    }

    @Test
    public void testUpdateOrderAddress_clearsAddressVerificationAudit() {
        Long orderId = 100L;
        TradeOrderDO order = new TradeOrderDO()
                .setId(orderId)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus())
                .setAddressVerification(buildAddressVerification());
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        TradeOrderUpdateAddressReqVO reqVO = new TradeOrderUpdateAddressReqVO()
                .setId(orderId)
                .setReceiverName("Jane Buyer")
                .setReceiverMobile("15500001111")
                .setReceiverAreaId(7310)
                .setReceiverDetailAddress("100 New St, Boston, MA 02108");

        tradeOrderUpdateService.updateOrderAddress(reqVO);

        verify(tradeOrderMapper).updateById(org.mockito.ArgumentMatchers.<TradeOrderDO>argThat(update ->
                orderId.equals(update.getId())
                        && update.getAddressVerification() == null
                        && "100 New St, Boston, MA 02108".equals(update.getReceiverDetailAddress())));
    }

    @Test
    public void testDeliveryOrder_rejectsRiskAddressVerificationWithoutAcknowledgement() {
        Long orderId = 100L;
        TradeOrderDO order = new TradeOrderDO()
                .setId(orderId)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus())
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus())
                .setDeliveryType(DeliveryTypeEnum.EXPRESS.getType())
                .setAddressVerification(buildFallbackAddressVerification());
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);

        TradeOrderDeliveryReqVO reqVO = new TradeOrderDeliveryReqVO();
        reqVO.setId(orderId);
        reqVO.setLogisticsId(1L);
        reqVO.setLogisticsNo("SF123456789");

        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> tradeOrderUpdateService.deliveryOrder(reqVO));

        assertEquals(ORDER_DELIVERY_FAIL_ADDRESS_VERIFICATION_NEEDS_REVIEW.getCode(), serviceException.getCode());
        verify(tradeOrderMapper, never()).updateByIdAndStatus(eq(orderId), any(), any());
    }

    @Test
    public void testAddressVerification_allowsNullUpdate() throws NoSuchFieldException {
        Field field = TradeOrderDO.class.getDeclaredField("addressVerification");
        TableField tableField = field.getAnnotation(TableField.class);

        assertEquals(FieldStrategy.ALWAYS, tableField.updateStrategy());
    }

    @Test
    public void testCreateOrder_zeroPayPriceMarksOrderPaidWithoutPayOrder() {
        Long userId = 10L;
        Long addressId = 200L;
        AppTradeOrderCreateReqVO reqVO = buildCreateOrderReqVO(addressId);
        reqVO.setAddressVerification(buildAddressVerification());
        when(cartService.getCartList(eq(userId), any())).thenReturn(Collections.emptyList());
        when(tradePriceService.calculateOrderPrice(any())).thenReturn(buildPriceCalculateRespBO());
        when(addressApi.getAddress(addressId, userId)).thenReturn(CommonResult.success(buildMemberAddress(addressId,
                "1600 Amphitheatre Pkwy, Mountain View, CA 94043")));
        when(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX)).thenReturn("T202606170001");
        doAnswer(invocation -> {
            TradeOrderDO order = invocation.getArgument(0);
            order.setId(100L);
            return 1;
        }).when(tradeOrderMapper).insert(org.mockito.ArgumentMatchers.<TradeOrderDO>any());

        TradeOrderDO order = tradeOrderUpdateService.createOrder(userId, reqVO);

        assertEquals(100L, order.getId());
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(), order.getStatus());
        assertEquals(Boolean.TRUE, order.getPayStatus());
        verify(payOrderApi, never()).createOrder(any());
        verify(tradeOrderMapper).updateById(org.mockito.ArgumentMatchers.<TradeOrderDO>argThat(update ->
                Long.valueOf(100L).equals(update.getId())
                        && TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(update.getStatus())
                        && Boolean.TRUE.equals(update.getPayStatus())
                        && update.getPayTime() != null));
    }

    @Test
    public void testUpdateOrderPaid_usesPaymentSuccessTimeFromPayOrder() {
        Long orderId = 100L;
        Long payOrderId = 900L;
        LocalDateTime paymentSuccessTime = LocalDateTime.of(2026, 6, 17, 9, 30);
        TradeOrderDO order = new TradeOrderDO()
                .setId(orderId)
                .setUserId(10L)
                .setStatus(TradeOrderStatusEnum.UNPAID.getStatus())
                .setPayStatus(false)
                .setPayOrderId(payOrderId)
                .setPayPrice(1200);
        PayOrderRespDTO payOrder = new PayOrderRespDTO()
                .setId(payOrderId)
                .setStatus(PayOrderStatusEnum.SUCCESS.getStatus())
                .setPrice(1200)
                .setMerchantOrderId(orderId.toString())
                .setChannelCode("stripe_card")
                .setSuccessTime(paymentSuccessTime);
        when(tradeOrderMapper.selectById(orderId)).thenReturn(order);
        when(payOrderApi.getOrder(payOrderId)).thenReturn(CommonResult.success(payOrder));
        when(tradeOrderMapper.updateByIdAndStatus(eq(orderId), eq(TradeOrderStatusEnum.UNPAID.getStatus()),
                any())).thenReturn(1);
        when(tradeOrderItemMapper.selectListByOrderId(orderId)).thenReturn(Collections.emptyList());

        tradeOrderUpdateService.updateOrderPaid(orderId, payOrderId);

        verify(tradeOrderMapper).updateByIdAndStatus(eq(orderId), eq(TradeOrderStatusEnum.UNPAID.getStatus()),
                org.mockito.ArgumentMatchers.<TradeOrderDO>argThat(update ->
                        Boolean.TRUE.equals(update.getPayStatus())
                                && TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(update.getStatus())
                                && paymentSuccessTime.equals(update.getPayTime())
                                && "stripe_card".equals(update.getPayChannelCode())));
    }

    private static AppTradeOrderCreateReqVO buildCreateOrderReqVO(Long addressId) {
        AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
        item.setSkuId(100L);
        item.setCount(1);

        AppTradeOrderCreateReqVO reqVO = new AppTradeOrderCreateReqVO();
        reqVO.setItems(Collections.singletonList(item));
        reqVO.setPointStatus(false);
        reqVO.setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        reqVO.setAddressId(addressId);
        return reqVO;
    }

    private static MemberAddressRespDTO buildMemberAddress(Long id, String detailAddress) {
        MemberAddressRespDTO address = new MemberAddressRespDTO();
        address.setId(id);
        address.setName("Jane Buyer");
        address.setMobile("15500001111");
        address.setAreaId(1);
        address.setDetailAddress(detailAddress);
        return address;
    }

    private static TradePriceCalculateRespBO buildPriceCalculateRespBO() {
        TradePriceCalculateRespBO.Price price = new TradePriceCalculateRespBO.Price();
        price.setTotalPrice(1000);
        price.setDiscountPrice(0);
        price.setDeliveryPrice(0);
        price.setCouponPrice(0);
        price.setPointPrice(0);
        price.setVipPrice(0);
        price.setPayPrice(0);

        TradePriceCalculateRespBO.OrderItem item = new TradePriceCalculateRespBO.OrderItem();
        item.setSkuId(100L);
        item.setSpuId(10L);
        item.setCount(1);
        item.setSelected(true);
        item.setPrice(1000);
        item.setPayPrice(1000);
        item.setSpuName("Test Chair");

        TradePriceCalculateRespBO respBO = new TradePriceCalculateRespBO();
        respBO.setPrice(price);
        respBO.setItems(Collections.singletonList(item));
        return respBO;
    }

    private static Map<String, Object> buildAddressVerification() {
        return buildAddressVerification("1600 Amphitheatre Pkwy", "Mountain View", "CA", "94043");
    }

    private static Map<String, Object> buildAddressVerification(String street, String city, String state,
                                                               String postalCode) {
        Map<String, Object> selectedAddress = new HashMap<>();
        selectedAddress.put("street", street);
        selectedAddress.put("city", city);
        selectedAddress.put("state", state);
        selectedAddress.put("postalCode", postalCode);

        Map<String, Object> addressVerification = new HashMap<>();
        addressVerification.put("source", "google-address-validation");
        addressVerification.put("addressSource", "saved");
        addressVerification.put("status", "verified");
        addressVerification.put("choice", "original");
        addressVerification.put("confirmedAt", "2026-06-16T10:00:00.000Z");
        addressVerification.put("selectedAddress", selectedAddress);
        return addressVerification;
    }

    private static Map<String, Object> buildFallbackAddressVerification() {
        Map<String, Object> addressVerification = buildAddressVerification();
        addressVerification.put("providerStatus", "fallback");
        return addressVerification;
    }

}
