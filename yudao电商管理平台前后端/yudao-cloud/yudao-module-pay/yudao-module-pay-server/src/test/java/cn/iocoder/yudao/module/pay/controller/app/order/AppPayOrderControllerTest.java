package cn.iocoder.yudao.module.pay.controller.app.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.pay.controller.app.order.vo.AppPayOrderSubmitReqVO;
import cn.iocoder.yudao.module.pay.controller.app.order.vo.AppPayOrderSubmitRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.wallet.PayWalletService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import javax.validation.Valid;

import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppPayOrderController} 的单元测试。
 */
public class AppPayOrderControllerTest extends BaseMockitoUnitTest {

    private static final Long LOGIN_USER_ID = 100L;

    @InjectMocks
    private AppPayOrderController controller;

    @Mock
    private PayOrderService payOrderService;
    @Mock
    private PayWalletService payWalletService;

    @Test
    public void testSubmitPayOrder_nullRequest_returnNull() {
        try (MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            webFrameworkUtils.when(WebFrameworkUtils::getLoginUserId).thenReturn(LOGIN_USER_ID);

            // 璋冪敤
            CommonResult<AppPayOrderSubmitRespVO> result = controller.submitPayOrder(null);

            // 鏂█
            assertEquals(0, result.getCode());
            assertNull(result.getData());
            verify(payWalletService, never()).getOrCreateWallet(any(), any());
            verify(payOrderService, never()).submitOrder(any(), any());
        }
    }

    @Test
    public void testSubmitPayOrder_anonymousUser_returnNull() {
        // 鍑嗗鍙傛暟
        AppPayOrderSubmitReqVO reqVO = new AppPayOrderSubmitReqVO();
        reqVO.setId(randomLongId());
        reqVO.setChannelCode(PayChannelEnum.WALLET.getCode());

        try (MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            webFrameworkUtils.when(WebFrameworkUtils::getLoginUserId).thenReturn(null);

            // 璋冪敤
            CommonResult<AppPayOrderSubmitRespVO> result = controller.submitPayOrder(reqVO);

            // 鏂█
            assertEquals(0, result.getCode());
            assertNull(result.getData());
            verify(payWalletService, never()).getOrCreateWallet(any(), any());
            verify(payOrderService, never()).getOrder(any(Long.class));
            verify(payOrderService, never()).submitOrder(any(), any());
        }
    }

    @Test
    public void testSubmitPayOrder_requestBodyIsValidated() throws NoSuchMethodException {
        assertTrue(AppPayOrderController.class
                .getMethod("submitPayOrder", AppPayOrderSubmitReqVO.class)
                .getParameters()[0]
                .isAnnotationPresent(Valid.class));
    }

    @Test
    public void testSubmitPayOrder_otherUserOrder_returnNull() {
        // 准备参数
        Long payOrderId = randomLongId();
        AppPayOrderSubmitReqVO reqVO = new AppPayOrderSubmitReqVO();
        reqVO.setId(payOrderId);
        reqVO.setChannelCode(PayChannelEnum.ALIPAY_PC.getCode());
        PayOrderDO order = new PayOrderDO().setId(payOrderId).setUserId(200L);
        when(payOrderService.getOrder(eq(payOrderId))).thenReturn(order);

        try (MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            webFrameworkUtils.when(WebFrameworkUtils::getLoginUserId).thenReturn(LOGIN_USER_ID);

            // 调用
            CommonResult<AppPayOrderSubmitRespVO> result = controller.submitPayOrder(reqVO);

            // 断言
            assertEquals(0, result.getCode());
            assertNull(result.getData());
            verify(payOrderService, never()).submitOrder(any(), any());
        }
    }

    @Test
    public void testSubmitPayOrder_missingPayOrderId_noWalletSideEffect() {
        // 准备参数
        AppPayOrderSubmitReqVO reqVO = new AppPayOrderSubmitReqVO();
        reqVO.setChannelCode(PayChannelEnum.WALLET.getCode());

        try (MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            webFrameworkUtils.when(WebFrameworkUtils::getLoginUserId).thenReturn(LOGIN_USER_ID);

            // 调用
            CommonResult<AppPayOrderSubmitRespVO> result = controller.submitPayOrder(reqVO);

            // 断言
            assertEquals(0, result.getCode());
            assertNull(result.getData());
            verify(payWalletService, never()).getOrCreateWallet(any(), any());
            verify(payOrderService, never()).submitOrder(any(), any());
        }
    }

    @Test
    public void testGetOrder_anonymousUser_returnNull() {
        try (MockedStatic<WebFrameworkUtils> webFrameworkUtils = mockStatic(WebFrameworkUtils.class)) {
            webFrameworkUtils.when(WebFrameworkUtils::getLoginUserId).thenReturn(null);

            // 璋冪敤
            CommonResult<?> result = controller.getOrder(randomLongId(), null, false);

            // 鏂█
            assertEquals(0, result.getCode());
            assertNull(result.getData());
            verify(payOrderService, never()).getOrder(any(Long.class));
        }
    }

}
