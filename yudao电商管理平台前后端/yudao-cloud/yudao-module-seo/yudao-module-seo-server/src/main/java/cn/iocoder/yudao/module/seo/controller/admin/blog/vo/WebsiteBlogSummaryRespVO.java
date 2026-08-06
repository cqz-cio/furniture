package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteBlogSummaryRespVO {

    private Long total;
    private Long draft;
    private Long published;
    private Long offline;

}
