package cn.iocoder.yudao.module.statistics.service.pay;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.statistics.dal.mysql.pay.PayWalletStatisticsMapper;
import cn.iocoder.yudao.module.statistics.service.pay.bo.RechargeSummaryRespBO;
import cn.iocoder.yudao.module.statistics.service.trade.bo.WalletSummaryRespBO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class PayWalletStatisticsServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PayWalletStatisticsServiceImpl payWalletStatisticsService;

    @Mock
    private PayWalletStatisticsMapper payWalletStatisticsMapper;

    @Test
    public void testGetWalletSummary_whenWalletTableMissing_returnsZeroSummary() {
        RuntimeException tableMissingException = buildTableMissingException("pay_wallet_recharge");
        when(payWalletStatisticsMapper.selectRechargeSummaryByPayTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class), anyBoolean()))
                .thenThrow(tableMissingException);

        WalletSummaryRespBO summary = payWalletStatisticsService.getWalletSummary(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(0, summary.getWalletPayPrice());
        assertEquals(0, summary.getRechargePayCount());
        assertEquals(0, summary.getRechargePayPrice());
        assertEquals(0, summary.getRechargeRefundCount());
        assertEquals(0, summary.getRechargeRefundPrice());
    }

    @Test
    public void testGetUserRechargeSummary_whenWalletTableMissing_returnsZeroSummary() {
        when(payWalletStatisticsMapper.selectRechargeSummaryGroupByWalletId(any(), any(), anyBoolean()))
                .thenThrow(buildTableMissingException("pay_wallet_recharge"));

        RechargeSummaryRespBO summary = payWalletStatisticsService.getUserRechargeSummary(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(0, summary.getRechargeUserCount());
        assertEquals(0, summary.getRechargePrice());
    }

    @Test
    public void testGetRechargePriceSummary_whenWalletTableMissing_returnsZero() {
        when(payWalletStatisticsMapper.selectRechargePriceSummary(anyBoolean()))
                .thenThrow(buildTableMissingException("pay_wallet_recharge"));

        Integer rechargePrice = payWalletStatisticsService.getRechargePriceSummary();

        assertEquals(0, rechargePrice);
    }

    @Test
    public void testGetRechargePriceSummary_whenUnexpectedException_rethrows() {
        RuntimeException exception = new RuntimeException("boom");
        when(payWalletStatisticsMapper.selectRechargePriceSummary(anyBoolean())).thenThrow(exception);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> payWalletStatisticsService.getRechargePriceSummary());

        assertEquals("boom", thrown.getMessage());
    }

    private RuntimeException buildTableMissingException(String tableName) {
        return new RuntimeException("query failed",
                new RuntimeException("Table 'ruoyi-vue-pro." + tableName + "' doesn't exist"));
    }
}
