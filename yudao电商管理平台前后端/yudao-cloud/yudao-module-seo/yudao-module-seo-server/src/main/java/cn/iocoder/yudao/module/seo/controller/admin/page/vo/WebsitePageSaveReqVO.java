package cn.iocoder.yudao.module.seo.controller.admin.page.vo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebsitePageSaveReqVO extends WebsitePageVersionReqVO {
    @NotNull private JsonNode content;
}
