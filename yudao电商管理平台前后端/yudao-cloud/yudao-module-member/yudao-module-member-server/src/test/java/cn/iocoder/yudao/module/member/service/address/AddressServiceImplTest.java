package cn.iocoder.yudao.module.member.service.address;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.controller.app.address.vo.AppAddressUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.address.MemberAddressDO;
import cn.iocoder.yudao.module.member.dal.mysql.address.MemberAddressMapper;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AddressServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AddressServiceImpl addressService;

    @Mock
    private MemberAddressMapper memberAddressMapper;

    @Test
    public void testUpdateAddress_whenDeliveryAddressUnchangedAndAuditOmitted_keepsAddressVerificationAudit()
            throws Exception {
        Map<String, Object> addressVerification = buildAddressVerification();
        when(memberAddressMapper.selectByIdAndUserId(eq(100L), eq(200L))).thenReturn(new MemberAddressDO()
                .setId(100L)
                .setUserId(200L)
                .setName("Ada Lovelace")
                .setMobile("555-0100")
                .setAreaId(1L)
                .setDetailAddress("12 MAIN ST, New York, NY 10001")
                .setDefaultStatus(false)
                .setAddressVerification(addressVerification));

        AppAddressUpdateReqVO reqVO = buildUpdateReqVO("12 MAIN ST, New York, NY 10001");

        addressService.updateAddress(200L, reqVO);

        verify(memberAddressMapper).updateById(argThat((MemberAddressDO update) -> {
            assertEquals(addressVerification, update.getAddressVerification());
            return true;
        }));
    }

    @Test
    public void testAddressVerificationField_allowsClearingStaleAudit() throws Exception {
        TableField tableField = MemberAddressDO.class.getDeclaredField("addressVerification")
                .getAnnotation(TableField.class);

        assertEquals(FieldStrategy.ALWAYS, tableField.updateStrategy());
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

    private static AppAddressUpdateReqVO buildUpdateReqVO(String detailAddress) {
        AppAddressUpdateReqVO reqVO = new AppAddressUpdateReqVO();
        reqVO.setId(100L);
        reqVO.setName("Ada Lovelace");
        reqVO.setMobile("555-0100");
        reqVO.setAreaId(1L);
        reqVO.setDetailAddress(detailAddress);
        reqVO.setDefaultStatus(true);
        return reqVO;
    }

    private static Map<String, Object> buildAddressVerification() {
        Map<String, Object> addressVerification = new HashMap<>();
        addressVerification.put("source", "google-address-validation");
        addressVerification.put("status", "verified");
        addressVerification.put("choice", "original");
        addressVerification.put("confirmedAt", "2026-06-16T10:00:00.000Z");
        return addressVerification;
    }

}
