package cn.iocoder.yudao.module.trade.convert.order;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDetailRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TradeOrderConvertTest {

    @Test
    public void testConvertCreateReq_keepsAddressVerificationAudit() {
        AppTradeOrderCreateReqVO createReqVO = new AppTradeOrderCreateReqVO();
        createReqVO.setAddressVerification(buildAddressVerification());

        TradeOrderDO orderDO = TradeOrderConvert.INSTANCE.convert(100L, createReqVO, buildPriceCalculateResp());

        assertEquals("google-address-validation", orderDO.getAddressVerification().get("source"));
        assertEquals("saved", orderDO.getAddressVerification().get("addressSource"));
        assertEquals("suggested", orderDO.getAddressVerification().get("status"));
        assertEquals("google-response-1", orderDO.getAddressVerification().get("providerResponseId"));
    }

    @Test
    public void testConvertPayOrderCreateReq_keepsMemberIdentity() {
        TradeOrderDO orderDO = new TradeOrderDO();
        orderDO.setId(200L);
        orderDO.setUserId(100L);
        orderDO.setUserIp("127.0.0.1");
        orderDO.setPayPrice(1999);

        TradeOrderItemDO orderItemDO = new TradeOrderItemDO();
        orderItemDO.setSpuName("Oak dining chair");

        TradeOrderProperties properties = new TradeOrderProperties();
        properties.setPayAppKey("mall");
        properties.setPayExpireTime(Duration.ofMinutes(30));

        PayOrderCreateReqDTO reqDTO = TradeOrderConvert.INSTANCE.convert(orderDO, Collections.singletonList(orderItemDO),
                properties);

        assertEquals("mall", reqDTO.getAppKey());
        assertEquals(100L, reqDTO.getUserId());
        assertEquals(UserTypeEnum.MEMBER.getValue(), reqDTO.getUserType());
        assertEquals("200", reqDTO.getMerchantOrderId());
        assertEquals(1999, reqDTO.getPrice());
    }

    @Test
    public void testConvertAppDetail_exposesAddressVerificationAudit() {
        TradeOrderDO orderDO = new TradeOrderDO();
        orderDO.setAddressVerification(buildAddressVerification());

        AppTradeOrderDetailRespVO respVO = TradeOrderConvert.INSTANCE.convert3(orderDO, Collections.emptyList());

        assertEquals("google-address-validation", respVO.getAddressVerification().get("source"));
        assertEquals("saved", respVO.getAddressVerification().get("addressSource"));
        assertEquals("suggested", respVO.getAddressVerification().get("status"));
        assertEquals("google-response-1", respVO.getAddressVerification().get("providerResponseId"));
    }

    @Test
    public void testConvertAdminDetail_exposesAddressVerificationAudit() {
        TradeOrderDO orderDO = new TradeOrderDO();
        orderDO.setAddressVerification(buildAddressVerification());

        TradeOrderDetailRespVO respVO = TradeOrderConvert.INSTANCE.convert2(orderDO, Collections.emptyList());

        assertEquals("google-address-validation", respVO.getAddressVerification().get("source"));
        assertEquals("saved", respVO.getAddressVerification().get("addressSource"));
        assertEquals("suggested", respVO.getAddressVerification().get("status"));
        assertEquals("google-response-1", respVO.getAddressVerification().get("providerResponseId"));
    }

    @Test
    public void testCreateTablesSql_containsAddressVerificationColumn() throws IOException {
        String schema = readClasspathResource("/sql/create_tables.sql");

        assertTrue(schema.contains("\"address_verification\""));
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(path);
             Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static Map<String, Object> buildAddressVerification() {
        Map<String, Object> addressVerification = new HashMap<>();
        addressVerification.put("source", "google-address-validation");
        addressVerification.put("addressSource", "saved");
        addressVerification.put("status", "suggested");
        addressVerification.put("providerResponseId", "google-response-1");
        return addressVerification;
    }

    private static TradePriceCalculateRespBO buildPriceCalculateResp() {
        TradePriceCalculateRespBO.Price price = new TradePriceCalculateRespBO.Price();
        price.setTotalPrice(1000);
        price.setDiscountPrice(0);
        price.setDeliveryPrice(0);
        price.setCouponPrice(0);
        price.setPointPrice(0);
        price.setVipPrice(0);
        price.setPayPrice(1000);

        TradePriceCalculateRespBO calculateRespBO = new TradePriceCalculateRespBO();
        calculateRespBO.setPrice(price);
        return calculateRespBO;
    }

}
