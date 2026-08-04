package cn.iocoder.yudao.module.crm.dal.mysql.clue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.crm.controller.admin.clue.vo.CrmCluePageReqVO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.common.CrmSceneTypeEnum;
import cn.iocoder.yudao.module.crm.util.CrmPermissionUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 线索 Mapper
 *
 * @author Wanwan
 */
@Mapper
public interface CrmClueMapper extends BaseMapperX<CrmClueDO> {

    default PageResult<CrmClueDO> selectPage(CrmCluePageReqVO pageReqVO, Long userId) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        // 拼接数据权限的查询条件
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                CrmClueDO::getId, userId, pageReqVO.getSceneType());
        // 拼接自身的查询条件
        query.selectAll(CrmClueDO.class)
                .likeIfPresent(CrmClueDO::getName, pageReqVO.getName())
                .likeIfPresent(CrmClueDO::getContactName, pageReqVO.getContactName())
                .likeIfPresent(CrmClueDO::getCompanyName, pageReqVO.getCompanyName())
                .likeIfPresent(CrmClueDO::getEmail, pageReqVO.getEmail())
                .likeIfPresent(CrmClueDO::getInquirySubject, pageReqVO.getInquirySubject())
                .eqIfPresent(CrmClueDO::getProcessStatus, pageReqVO.getProcessStatus())
                .eqIfPresent(CrmClueDO::getTestData, pageReqVO.getTestData())
                .eqIfPresent(CrmClueDO::getPriority, pageReqVO.getPriority())
                .eqIfPresent(CrmClueDO::getSalesStage, pageReqVO.getSalesStage())
                .eqIfPresent(CrmClueDO::getCustomerId, pageReqVO.getCustomerId())
                .eqIfPresent(CrmClueDO::getTransformStatus, pageReqVO.getTransformStatus())
                .likeIfPresent(CrmClueDO::getTelephone, pageReqVO.getTelephone())
                .likeIfPresent(CrmClueDO::getMobile, pageReqVO.getMobile())
                .eqIfPresent(CrmClueDO::getIndustryId, pageReqVO.getIndustryId())
                .eqIfPresent(CrmClueDO::getLevel, pageReqVO.getLevel())
                .eqIfPresent(CrmClueDO::getSource, pageReqVO.getSource())
                .eqIfPresent(CrmClueDO::getFollowUpStatus, pageReqVO.getFollowUpStatus())
                .betweenIfPresent(CrmClueDO::getCreateTime, pageReqVO.getCreateTime())
                .betweenIfPresent(CrmClueDO::getSubmittedAt, pageReqVO.getSubmittedAt())
                .isNotNull(CrmClueDO::getExternalInquiryId)
                .orderByDesc(CrmClueDO::getId);
        return selectJoinPage(pageReqVO, CrmClueDO.class, query);
    }

    default CrmClueDO selectByExternalInquiryId(String externalInquiryId) {
        return selectOne(CrmClueDO::getExternalInquiryId, externalInquiryId);
    }

    default Long selectInquiryCount(Long userId, Integer processStatus, Boolean testData) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                CrmClueDO::getId, userId, null);
        query.eqIfPresent(CrmClueDO::getProcessStatus, processStatus)
                .eqIfPresent(CrmClueDO::getTestData, testData)
                .isNotNull(CrmClueDO::getExternalInquiryId);
        return selectCount(query);
    }

    default Long selectOverdueInquiryCount(Long userId, Boolean testData,
                                           java.time.LocalDateTime overdueBefore) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                CrmClueDO::getId, userId, null);
        query.eq(CrmClueDO::getProcessStatus, 0)
                .eqIfPresent(CrmClueDO::getTestData, testData)
                .le(CrmClueDO::getSubmittedAt, overdueBefore)
                .isNotNull(CrmClueDO::getExternalInquiryId);
        return selectCount(query);
    }

    default int updateProcessStatus(Long id, Integer processStatus,
                                    java.time.LocalDateTime processedAt, String remark) {
        LambdaUpdateWrapper<CrmClueDO> update = new LambdaUpdateWrapper<CrmClueDO>()
                .eq(CrmClueDO::getId, id)
                .set(CrmClueDO::getProcessStatus, processStatus)
                .set(CrmClueDO::getProcessedAt, processedAt);
        if (remark != null) {
            update.set(CrmClueDO::getRemark, remark);
        }
        return update(null, update);
    }

    default int markFirstResponse(Long id, Integer qualifyingStage) {
        return update(null, new LambdaUpdateWrapper<CrmClueDO>()
                .eq(CrmClueDO::getId, id)
                .set(CrmClueDO::getFollowUpStatus, true)
                .setSql("first_response_at = COALESCE(first_response_at, CURRENT_TIMESTAMP)")
                .setSql("sales_stage = CASE WHEN sales_stage = 0 THEN {0} ELSE sales_stage END",
                        qualifyingStage));
    }

    default int updateSalesStage(Long id, Integer salesStage) {
        return update(null, new LambdaUpdateWrapper<CrmClueDO>()
                .eq(CrmClueDO::getId, id)
                .set(CrmClueDO::getSalesStage, salesStage));
    }

    default Long selectCountByFollow(Long userId) {
        MPJLambdaWrapperX<CrmClueDO> query = new MPJLambdaWrapperX<>();
        // 我负责的 + 非公海
        CrmPermissionUtils.appendPermissionCondition(query, CrmBizTypeEnum.CRM_CLUE.getType(),
                CrmClueDO::getId, userId, CrmSceneTypeEnum.OWNER.getType());
        // 未跟进 + 未转化
        query.eq(CrmClueDO::getFollowUpStatus, false)
                .eq(CrmClueDO::getTransformStatus, false);
        return selectCount(query);
    }

}
