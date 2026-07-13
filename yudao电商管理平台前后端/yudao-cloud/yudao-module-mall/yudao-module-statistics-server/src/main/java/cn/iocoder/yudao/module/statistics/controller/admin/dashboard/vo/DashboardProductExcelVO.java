package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class DashboardProductExcelVO {
    @ExcelProperty("SPU ID") private Long spuId;
    @ExcelProperty("商品名称") private String productName;
    @ExcelProperty("分类 ID") private Long categoryId;
    @ExcelProperty("详情页浏览量") private Long browseCount;
    @ExcelProperty("详情页访客数") private Long browseUserCount;
    @ExcelProperty("加购次数") private Long cartCount;
    @ExcelProperty("支付订单量") private Long orderCount;
    @ExcelProperty("支付件数") private Long orderPayCount;
    @ExcelProperty("支付金额（分）") private Long orderPayPrice;
    @ExcelProperty("退款金额（分）") private Long afterSaleRefundPrice;
    @ExcelProperty("已知成本（分）") private Long knownCostAmount;
    @ExcelProperty("完整成本（分）") private Long costAmount;
    @ExcelProperty("毛利润（分）") private Long grossProfit;
    @ExcelProperty("毛利率") private BigDecimal grossMarginPercent;
}
