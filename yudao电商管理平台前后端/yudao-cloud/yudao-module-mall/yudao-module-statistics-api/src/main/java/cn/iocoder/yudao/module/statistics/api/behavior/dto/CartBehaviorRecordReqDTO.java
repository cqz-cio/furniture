package cn.iocoder.yudao.module.statistics.api.behavior.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data public class CartBehaviorRecordReqDTO { @NotBlank private String eventId; @NotNull private Long userId; @NotNull private Long spuId; @NotNull private Long skuId; @NotNull @Min(1) private Integer quantity; private String visitorId; private String sessionId; @javax.validation.constraints.Size(max=512) private String consentEvidence; }
