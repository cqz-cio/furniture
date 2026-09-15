package cn.iocoder.yudao.module.seo.controller.admin.media.vo;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @EqualsAndHashCode(callSuper = true)
public class WebsiteMediaPageReqVO extends PageParam {
    @Size(max=160) private String name;
    @Pattern(regexp="image|document") private String kind;
    private Boolean archived = false;
}
