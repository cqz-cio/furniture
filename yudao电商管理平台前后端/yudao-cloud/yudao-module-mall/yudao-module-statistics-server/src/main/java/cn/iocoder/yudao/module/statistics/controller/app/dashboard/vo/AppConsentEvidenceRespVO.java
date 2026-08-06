package cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppConsentEvidenceRespVO {
    private String evidence;
    private Long expiresAtEpochSeconds;
    private String policyVersion;
}
