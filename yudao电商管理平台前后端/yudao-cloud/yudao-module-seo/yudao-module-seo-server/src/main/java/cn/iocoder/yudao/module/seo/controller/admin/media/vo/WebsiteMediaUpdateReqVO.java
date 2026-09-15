package cn.iocoder.yudao.module.seo.controller.admin.media.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class WebsiteMediaUpdateReqVO {
    @NotNull @Positive private Long id;
    @NotBlank @Size(max=160) private String name;
    @NotNull @Size(max=240) private String alt;
}
