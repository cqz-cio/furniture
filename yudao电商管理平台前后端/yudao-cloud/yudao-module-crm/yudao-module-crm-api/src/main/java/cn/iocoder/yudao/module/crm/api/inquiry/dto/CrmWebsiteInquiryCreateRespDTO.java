package cn.iocoder.yudao.module.crm.api.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 官网询盘写入 CRM Response DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrmWebsiteInquiryCreateRespDTO {

    private Long inquiryId;
    private Boolean created;

}
