package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Raw snippets are stored as text and only executed by the public website. */
@Data
public class WebsiteCodeContent {
    @Valid @NotNull private Section header = new Section();
    @Valid @NotNull private Section body = new Section();
    @Valid @NotNull private Section footer = new Section();

    @Data
    public static class Section {
        @NotNull private Boolean enabled = false;
        @NotNull @Size(max = 100000) private String code = "";
    }
}
