package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquiryProcessStatusEnum;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquiryPriorityEnum;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquirySalesStageEnum;
import cn.iocoder.yudao.module.crm.enums.common.CrmSceneTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 线索分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmCluePageReqVO extends PageParam {

    @Schema(description = "线索名称", example = "线索xxx")
    private String name;

    @Schema(description = "联系人姓名", example = "Alex Morgan")
    private String contactName;

    @Schema(description = "公司名称", example = "Northstar Interiors")
    private String companyName;

    @Schema(description = "邮箱", example = "alex@example.com")
    private String email;

    @Schema(description = "询盘主题", example = "Hotel dining chair project")
    private String inquirySubject;

    @Schema(description = "处理状态", example = "10")
    @InEnum(CrmInquiryProcessStatusEnum.class)
    private Integer processStatus;

    @Schema(description = "是否测试数据。经营页面默认传 false；不传表示全部", example = "false")
    private Boolean testData;

    @Schema(description = "询盘优先级", example = "20")
    @InEnum(CrmInquiryPriorityEnum.class)
    private Integer priority;

    @Schema(description = "销售阶段", example = "10")
    @InEnum(CrmInquirySalesStageEnum.class)
    private Integer salesStage;

    @Schema(description = "关联客户编号", example = "1024")
    private Long customerId;

    @Schema(description = "转化状态", example = "2048")
    private Boolean transformStatus;

    @Schema(description = "电话", example = "18000000000")
    private String telephone;

    @Schema(description = "手机号", example = "18000000000")
    private String mobile;

    @Schema(description = "场景类型", example = "1")
    @InEnum(CrmSceneTypeEnum.class)
    private Integer sceneType; // 场景类型，为 null 时则表示全部

    @Schema(description = "所属行业", example = "1")
    private Integer industryId;

    @Schema(description = "客户等级", example = "1")
    private Integer level;

    @Schema(description = "客户来源", example = "1")
    private Integer source;

    @Schema(description = "跟进状态", example = "true")
    private Boolean followUpStatus;

    @Schema(description = "创建时间", example = "[2023-01-01 00:00:00, 2023-01-31 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

    @Schema(description = "网页提交时间", example = "[2026-07-01 00:00:00, 2026-07-31 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] submittedAt;

}
