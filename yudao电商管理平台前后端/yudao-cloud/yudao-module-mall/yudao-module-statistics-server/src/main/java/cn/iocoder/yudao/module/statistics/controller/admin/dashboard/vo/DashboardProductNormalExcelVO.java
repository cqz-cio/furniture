package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DashboardProductNormalExcelVO {
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
}
