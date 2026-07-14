package cn.iocoder.yudao.module.statistics.service.dashboard;
import lombok.Data;
@Data public class TrustedBehaviorEventCommand { private String eventId; private Long userId; private Long spuId; private Long skuId; private Integer quantity; private String rawVisitorId; private String rawSessionId; private String consentEvidence; }
