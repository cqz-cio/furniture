package cn.iocoder.yudao.module.member.convert.address;

import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressCreateReqVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressRespVO;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class AddressConvertTest {

    @Test
    public void testConvertCreateReq_keepsAddressVerificationAudit() {
        AppAddressCreateReqVO createReqVO = new AppAddressCreateReqVO();
        createReqVO.setName("Ada Lovelace");
        createReqVO.setMobile("555-0100");
        createReqVO.setAreaId(1L);
        createReqVO.setDetailAddress("12 MAIN ST, New York, NY 10001");
        createReqVO.setDefaultStatus(true);
        setProperty(createReqVO, "addressVerification", buildAddressVerification());

        MemberAddressDO addressDO = AddressConvert.INSTANCE.convert(createReqVO);

        assertEquals("google-address-validation", getAddressVerification(addressDO).get("source"));
        assertEquals("suggested", getAddressVerification(addressDO).get("status"));
        assertEquals("google-response-1", getAddressVerification(addressDO).get("providerResponseId"));
    }

    @Test
    public void testConvertUpdateReq_keepsAddressVerificationAudit() {
        AppAddressUpdateReqVO updateReqVO = new AppAddressUpdateReqVO();
        updateReqVO.setId(100L);
        updateReqVO.setName("Ada Lovelace");
        updateReqVO.setMobile("555-0100");
        updateReqVO.setAreaId(1L);
        updateReqVO.setDetailAddress("12 MAIN ST, New York, NY 10001");
        updateReqVO.setDefaultStatus(true);
        setProperty(updateReqVO, "addressVerification", buildAddressVerification());

        MemberAddressDO addressDO = AddressConvert.INSTANCE.convert(updateReqVO);

        assertEquals("google-address-validation", getAddressVerification(addressDO).get("source"));
        assertEquals("suggested", getAddressVerification(addressDO).get("status"));
    }

    @Test
    public void testConvertAppResp_exposesAddressVerificationAudit() {
        MemberAddressDO addressDO = new MemberAddressDO();
        addressDO.setId(100L);
        addressDO.setUserId(200L);
        addressDO.setName("Ada Lovelace");
        addressDO.setMobile("555-0100");
        addressDO.setAreaId(1L);
        addressDO.setDetailAddress("12 MAIN ST, New York, NY 10001");
        addressDO.setDefaultStatus(true);
        setProperty(addressDO, "addressVerification", buildAddressVerification());

        AppAddressRespVO respVO = AddressConvert.INSTANCE.convert(addressDO);

        assertEquals("google-address-validation", getAddressVerification(respVO).get("source"));
        assertEquals("suggested", getAddressVerification(respVO).get("status"));
        assertEquals("google-response-1", getAddressVerification(respVO).get("providerResponseId"));
    }

    @Test
    public void testConvertRpcResp_exposesAddressVerificationAudit() {
        MemberAddressDO addressDO = new MemberAddressDO();
        addressDO.setId(100L);
        addressDO.setUserId(200L);
        addressDO.setName("Ada Lovelace");
        addressDO.setMobile("555-0100");
        addressDO.setAreaId(1L);
        addressDO.setDetailAddress("12 MAIN ST, New York, NY 10001");
        addressDO.setDefaultStatus(true);
        setProperty(addressDO, "addressVerification", buildAddressVerification());

        MemberAddressRespDTO respDTO = AddressConvert.INSTANCE.convert02(addressDO);

        assertEquals("google-address-validation", getAddressVerification(respDTO).get("source"));
        assertEquals("suggested", getAddressVerification(respDTO).get("status"));
    }

    private static Map<String, Object> buildAddressVerification() {
        Map<String, Object> addressVerification = new HashMap<>();
        addressVerification.put("source", "google-address-validation");
        addressVerification.put("status", "suggested");
        addressVerification.put("choice", "suggested");
        addressVerification.put("providerResponseId", "google-response-1");
        return addressVerification;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getAddressVerification(Object bean) {
        return (Map<String, Object>) getProperty(bean, "addressVerification");
    }

    private static void setProperty(Object bean, String property, Object value) {
        try {
            Method writeMethod = findSetter(bean, property);
            writeMethod.invoke(bean, value);
        } catch (ReflectiveOperationException ex) {
            fail("Unable to set " + bean.getClass().getSimpleName() + "." + property + ": " + ex.getMessage());
        }
    }

    private static Object getProperty(Object bean, String property) {
        try {
            Method readMethod = findGetter(bean, property);
            return readMethod.invoke(bean);
        } catch (ReflectiveOperationException ex) {
            fail("Unable to get " + bean.getClass().getSimpleName() + "." + property + ": " + ex.getMessage());
            return null;
        }
    }

    private static Method findSetter(Object bean, String property) {
        String setterName = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : bean.getClass().getMethods()) {
            if (setterName.equals(method.getName()) && method.getParameterCount() == 1) {
                return method;
            }
        }
        fail(bean.getClass().getSimpleName() + "." + property + " setter is missing");
        return null;
    }

    private static Method findGetter(Object bean, String property) {
        String getterName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : bean.getClass().getMethods()) {
            if (getterName.equals(method.getName()) && method.getParameterCount() == 0) {
                return method;
            }
        }
        fail(bean.getClass().getSimpleName() + "." + property + " getter is missing");
        return null;
    }

}
