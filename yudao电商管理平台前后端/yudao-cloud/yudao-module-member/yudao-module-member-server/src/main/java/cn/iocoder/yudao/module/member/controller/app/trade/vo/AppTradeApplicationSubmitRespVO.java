package cn.iocoder.yudao.module.member.controller.app.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "User App - Trade application submit response")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppTradeApplicationSubmitRespVO {

    @Schema(description = "Application id", example = "88")
    private Long id;

    @Schema(description = "Application status", example = "0")
    private Integer status;

}
