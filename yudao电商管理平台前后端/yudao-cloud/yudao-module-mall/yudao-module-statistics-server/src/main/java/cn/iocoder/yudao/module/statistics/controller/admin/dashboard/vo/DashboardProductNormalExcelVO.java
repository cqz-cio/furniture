package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DashboardProductNormalExcelVO {
    @ExcelProperty("SPU ID") private Long spuId;
    @ExcelProperty("详情页浏览量") private Integer browseCount;
    @ExcelProperty("详情页访客数") private Integer browseUserCount;
    @ExcelProperty("加购次数") private Integer cartCount;
    @ExcelProperty("支付订单量") private Integer orderCount;
    @ExcelProperty("支付件数") private Integer orderPayCount;
    @ExcelProperty("支付金额（分）") private Integer orderPayPrice;
    @ExcelProperty("退款金额（分）") private Integer afterSaleRefundPrice;
}
