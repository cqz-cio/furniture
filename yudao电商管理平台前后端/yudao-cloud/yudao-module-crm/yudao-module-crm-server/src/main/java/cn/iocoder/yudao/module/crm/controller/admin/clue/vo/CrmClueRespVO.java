package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import cn.iocoder.yudao.framework.excel.core.annotations.DictFormat;
import cn.iocoder.yudao.framework.excel.core.convert.DictConvert;
import cn.iocoder.yudao.module.infra.enums.DictTypeConstants;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 线索 Response VO")
@Data
@ToString(callSuper = true)
@ExcelIgnoreUnannotated
public class CrmClueRespVO {

    @Schema(description = "编号，主键自增", requiredMode = Schema.RequiredMode.REQUIRED, example = "10969")
    @ExcelProperty("编号")
    private Long id;

    @Schema(description = "线索名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "线索xxx")
    @ExcelProperty("线索名称")
    private String name;

    @Schema(description = "跟进状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @ExcelProperty(value = "跟进状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.BOOLEAN_STRING)
    private Boolean followUpStatus;

    @Schema(description = "最后跟进时间")
    @ExcelProperty("最后跟进时间")
    private LocalDateTime contactLastTime;

    @Schema(description = "最后跟进内容", example = "吃饭、睡觉、打逗逗")
    @ExcelProperty("最后跟进内容")
    private String contactLastContent;

    @Schema(description = "下次联系时间", example = "2023-10-18 01:00:00")
    @ExcelProperty("下次联系时间")
    private LocalDateTime contactNextTime;

    @Schema(description = "负责人编号")
    private Long ownerUserId;
    @Schema(description = "负责人名字", example = "25682")
    @ExcelProperty("负责人名字")
    private String ownerUserName;
    @Schema(description = "负责人部门")
    @ExcelProperty("负责人部门")
    private String ownerUserDeptName;

    @Schema(description = "转化状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @ExcelProperty(value = "转化状态", converter = DictConvert.class)
    @DictFormat(DictTypeConstants.BOOLEAN_STRING)
    private Boolean transformStatus;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "520")
    private Long customerId;
    @Schema(description = "客户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户名称")
    @ExcelProperty("客户名称")
    private String customerName;

    @Schema(description = "联系人编号", example = "521")
    private Long contactId;

    @Schema(description = "外部询盘编号", example = "2e82ae0d-5db2-4e94-bda0-0db994493885")
    @ExcelProperty("外部询盘编号")
    private String externalInquiryId;

    @Schema(description = "联系人姓名", example = "Alex Morgan")
    @ExcelProperty("联系人姓名")
    private String contactName;

    @Schema(description = "公司名称", example = "Northstar Interiors")
    @ExcelProperty("公司名称")
    private String companyName;

    @Schema(description = "国家或地区电话区号", example = "+44")
    @ExcelProperty("电话区号")
    private String countryCode;

    @Schema(description = "询盘主题", example = "Hotel dining chair project")
    @ExcelProperty("询盘主题")
    private String inquirySubject;

    @Schema(description = "询盘内容")
    @ExcelProperty("询盘内容")
    private String inquiryMessage;

    @Schema(description = "提交页面", example = "/products/dining-room")
    @ExcelProperty("提交页面")
    private String sourcePage;

    @Schema(description = "浏览器语言", example = "en-GB")
    @ExcelProperty("浏览器语言")
    private String locale;

    @Schema(description = "UTM 来源", example = "google")
    @ExcelProperty("UTM 来源")
    private String utmSource;

    @Schema(description = "UTM 媒介", example = "cpc")
    @ExcelProperty("UTM 媒介")
    private String utmMedium;

    @Schema(description = "UTM 活动", example = "summer-hotel")
    @ExcelProperty("UTM 活动")
    private String utmCampaign;

    @Schema(description = "网页提交时间")
    @ExcelProperty("提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "处理状态", example = "10")
    @ExcelProperty("处理状态")
    private Integer processStatus;

    @Schema(description = "处理完成时间")
    @ExcelProperty("处理完成时间")
    private LocalDateTime processedAt;

    @Schema(description = "手机号", example = "18000000000")
    @ExcelProperty("手机号")
    private String mobile;

    @Schema(description = "电话", example = "18000000000")
    @ExcelProperty("电话")
    private String telephone;

    @Schema(description = "QQ", example = "25682")
    @ExcelProperty("QQ")
    private String qq;

    @Schema(description = "wechat", example = "25682")
    @ExcelProperty("wechat")
    private String wechat;

    @Schema(description = "email", example = "25682")
    @ExcelProperty("email")
    private String email;

    @Schema(description = "地区编号", example = "1024")
    @ExcelProperty("地区编号")
    private Integer areaId;
    @Schema(description = "地区名称", example = "北京市")
    @ExcelProperty("地区名称")
    private String areaName;
    @Schema(description = "详细地址", example = "北京市成华大道")
    @ExcelProperty("详细地址")
    private String detailAddress;

    @Schema(description = "所属行业", requiredMode = Schema.RequiredMode.REQUIRED, example = "13563")
    @ExcelProperty(value = "所属行业", converter = DictConvert.class)
    @DictFormat(cn.iocoder.yudao.module.crm.enums.DictTypeConstants.CRM_CUSTOMER_INDUSTRY)
    private Integer industryId;

    @Schema(description = "客户等级", requiredMode = Schema.RequiredMode.REQUIRED, example = "13563")
    @ExcelProperty(value = "客户等级", converter = DictConvert.class)
    @DictFormat(cn.iocoder.yudao.module.crm.enums.DictTypeConstants.CRM_CUSTOMER_LEVEL)
    private Integer level;

    @Schema(description = "客户来源", requiredMode = Schema.RequiredMode.REQUIRED, example = "13563")
    @ExcelProperty(value = "客户来源", converter = DictConvert.class)
    @DictFormat(cn.iocoder.yudao.module.crm.enums.DictTypeConstants.CRM_CUSTOMER_SOURCE)
    private Integer source;

    @Schema(description = "备注", example = "随便")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建人", example = "1024")
    @ExcelProperty("创建人")
    private String creator;
    @Schema(description = "创建人名字", example = "芋道源码")
    @ExcelProperty("创建人名字")
    private String creatorName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @ExcelProperty("更新时间")
    private LocalDateTime updateTime;

}
