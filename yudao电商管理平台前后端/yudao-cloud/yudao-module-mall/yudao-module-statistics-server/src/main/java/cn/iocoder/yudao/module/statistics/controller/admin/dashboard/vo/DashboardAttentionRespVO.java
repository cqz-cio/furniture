package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class DashboardAttentionRespVO {
    private Integer highTrafficPvThreshold = 100;
    private BigDecimal lowConversionPercentThreshold = new BigDecimal("1.00");
    private Integer highRefundOrderThreshold = 10;
    private Long highRefundRevenueThreshold = 100000L;
    private BigDecimal highRefundPercentThreshold = new BigDecimal("10.00");
    private Integer lowMarginOrderThreshold = 5;
    private BigDecimal lowMarginPercentThreshold = new BigDecimal("10.00");
    private String disclaimer = "rule hint, not an automatic diagnosis";
    private List<Item> items = new ArrayList<>();
    private List<NotEvaluated> notEvaluated = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class Item {
        private Long spuId;
        private String riskType;
        private String copy;
    }

    @Data
    @Accessors(chain = true)
    public static class NotEvaluated {
        private Long spuId;
        private String riskType;
        private String reasonCode;
        private String copy;
    }
}
