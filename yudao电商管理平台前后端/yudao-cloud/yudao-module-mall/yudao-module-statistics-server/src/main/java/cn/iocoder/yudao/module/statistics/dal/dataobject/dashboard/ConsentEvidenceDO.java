package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("statistics_consent_evidence")
@KeySequence("statistics_consent_evidence_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConsentEvidenceDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String consentId;
    private String policyVersion;
    private String evidenceNonce;
    private Boolean preferences;
    private Boolean analytics;
    private Boolean marketing;
    private Long issuedEpoch;
    private Long expiresEpoch;
    private Long withdrawnEpoch;
}
