package cn.iocoder.yudao.module.product.controller.app.furniture.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Schema(description = "User App - Furniture assistant chat request")
@Data
public class FurnitureAssistantChatReqVO {

    @Schema(description = "User message", requiredMode = Schema.RequiredMode.REQUIRED, example = "cream fabric sofa under 8000")
    @NotEmpty(message = "User message cannot be empty")
    private String message;

}
