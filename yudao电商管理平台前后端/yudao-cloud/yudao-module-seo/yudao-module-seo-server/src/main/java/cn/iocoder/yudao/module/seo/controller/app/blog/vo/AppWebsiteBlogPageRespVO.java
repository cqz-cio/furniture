package cn.iocoder.yudao.module.seo.controller.app.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppWebsiteBlogPageRespVO {

    private List<AppWebsiteBlogArticleRespVO> items;
    private Long total;
    private Integer page;
    private Integer pageSize;

}
