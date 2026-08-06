package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.ConsentEvidenceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConsentEvidenceMapper extends BaseMapperX<ConsentEvidenceDO> {

    @Select("SELECT * FROM statistics_consent_evidence " +
            "WHERE tenant_id=#{tenantId} AND consent_id=#{consentId} AND deleted=FALSE LIMIT 1")
    ConsentEvidenceDO selectByConsentId(@Param("tenantId") Long tenantId,
                                        @Param("consentId") String consentId);

    @Select("SELECT COUNT(*) > 0 FROM statistics_consent_evidence " +
            "WHERE tenant_id=#{tenantId} AND evidence_nonce=#{nonce} AND analytics=TRUE " +
            "AND policy_version=#{policyVersion} " +
            "AND withdrawn_epoch IS NULL AND issued_epoch<=#{nowEpoch} AND expires_epoch>=#{nowEpoch} " +
            "AND deleted=FALSE")
    boolean isActiveAnalyticsEvidence(@Param("tenantId") Long tenantId,
                                      @Param("nonce") String nonce,
                                      @Param("policyVersion") String policyVersion,
                                      @Param("nowEpoch") long nowEpoch);

    @Update("UPDATE statistics_consent_evidence SET withdrawn_epoch=#{nowEpoch}, update_time=NOW(3) " +
            "WHERE tenant_id=#{tenantId} AND evidence_nonce=#{nonce} AND withdrawn_epoch IS NULL AND deleted=FALSE")
    int withdraw(@Param("tenantId") Long tenantId,
                 @Param("nonce") String nonce,
                 @Param("nowEpoch") long nowEpoch);

    @Delete("DELETE FROM statistics_consent_evidence WHERE tenant_id=#{tenantId} AND id IN " +
            "(SELECT id FROM (SELECT id FROM statistics_consent_evidence " +
            "WHERE tenant_id=#{tenantId} AND issued_epoch<#{cutoffEpoch} ORDER BY id LIMIT 10000) purge_ids)")
    int physicalDeleteExpiredBatch(@Param("tenantId") Long tenantId,
                                   @Param("cutoffEpoch") long cutoffEpoch);
}
