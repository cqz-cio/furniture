package cn.iocoder.yudao.module.statistics.service.pay;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.enums.wallet.PayWalletBizTypeEnum;
import cn.iocoder.yudao.module.statistics.dal.mysql.pay.PayWalletStatisticsMapper;
import cn.iocoder.yudao.module.statistics.service.pay.bo.RechargeSummaryRespBO;
import cn.iocoder.yudao.module.statistics.service.trade.bo.WalletSummaryRespBO;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

/**
 * Wallet statistics service implementation.
 *
 * @author owen
 */
@Service
@Validated
@Slf4j
public class PayWalletStatisticsServiceImpl implements PayWalletStatisticsService {

    @Resource
    private PayWalletStatisticsMapper payWalletStatisticsMapper;

    @Override
    public WalletSummaryRespBO getWalletSummary(LocalDateTime beginTime, LocalDateTime endTime) {
        try {
            WalletSummaryRespBO paySummary = payWalletStatisticsMapper.selectRechargeSummaryByPayTimeBetween(
                    beginTime, endTime, true);
            WalletSummaryRespBO refundSummary = payWalletStatisticsMapper.selectRechargeSummaryByRefundTimeBetween(
                    beginTime, endTime, PayRefundStatusEnum.SUCCESS.getStatus());
            Integer walletPayPrice = payWalletStatisticsMapper.selectPriceSummaryByBizTypeAndCreateTimeBetween(
                    beginTime, endTime, PayWalletBizTypeEnum.PAYMENT.getType());
            paySummary = paySummary != null ? paySummary : new WalletSummaryRespBO();
            refundSummary = refundSummary != null ? refundSummary : new WalletSummaryRespBO();
            // Merge recharge and refund stats, and normalize null aggregates to zero.
            paySummary.setWalletPayPrice(defaultIfNull(walletPayPrice))
                    .setRechargePayCount(defaultIfNull(paySummary.getRechargePayCount()))
                    .setRechargePayPrice(defaultIfNull(paySummary.getRechargePayPrice()))
                    .setRechargeRefundCount(defaultIfNull(refundSummary.getRechargeRefundCount()))
                    .setRechargeRefundPrice(defaultIfNull(refundSummary.getRechargeRefundPrice()));
            return paySummary;
        } catch (RuntimeException ex) {
            if (isWalletTableMissingException(ex)) {
                log.warn("[getWalletSummary][pay wallet tables missing, fallback to zero summary]");
                return buildEmptyWalletSummary();
            }
            throw ex;
        }
    }

    @Override
    public RechargeSummaryRespBO getUserRechargeSummary(LocalDateTime beginTime, LocalDateTime endTime) {
        try {
            RechargeSummaryRespBO summary = payWalletStatisticsMapper.selectRechargeSummaryGroupByWalletId(beginTime, endTime, true);
            if (summary == null) {
                return buildEmptyRechargeSummary();
            }
            summary.setRechargeUserCount(defaultIfNull(summary.getRechargeUserCount()))
                    .setRechargePrice(defaultIfNull(summary.getRechargePrice()));
            return summary;
        } catch (RuntimeException ex) {
            if (isWalletTableMissingException(ex)) {
                log.warn("[getUserRechargeSummary][pay wallet tables missing, fallback to zero summary]");
                return buildEmptyRechargeSummary();
            }
            throw ex;
        }
    }

    @Override
    public Integer getRechargePriceSummary() {
        try {
            return defaultIfNull(payWalletStatisticsMapper.selectRechargePriceSummary(Boolean.TRUE));
        } catch (RuntimeException ex) {
            if (isWalletTableMissingException(ex)) {
                log.warn("[getRechargePriceSummary][pay wallet tables missing, fallback to zero summary]");
                return 0;
            }
            throw ex;
        }
    }

    private boolean isWalletTableMissingException(Throwable ex) {
        String message = ExceptionUtil.getRootCauseMessage(ex);
        return message != null
                && message.contains("doesn't exist")
                && (message.contains("pay_wallet_recharge") || message.contains("pay_wallet_transaction"));
    }

    private WalletSummaryRespBO buildEmptyWalletSummary() {
        return new WalletSummaryRespBO()
                .setWalletPayPrice(0)
                .setRechargePayCount(0)
                .setRechargePayPrice(0)
                .setRechargeRefundCount(0)
                .setRechargeRefundPrice(0);
    }

    private RechargeSummaryRespBO buildEmptyRechargeSummary() {
        return new RechargeSummaryRespBO()
                .setRechargeUserCount(0)
                .setRechargePrice(0);
    }

    private Integer defaultIfNull(Integer value) {
        return value != null ? value : 0;
    }

}
