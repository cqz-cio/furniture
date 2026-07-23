package cn.iocoder.yudao.module.seo.dal.mysql.analysis;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoKeywordAnalysisDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SeoKeywordAnalysisMapper extends BaseMapperX<SeoKeywordAnalysisDO> {

    default List<SeoKeywordAnalysisDO> selectListByAnalysisId(Long analysisId) {
        return selectList(new LambdaQueryWrapperX<SeoKeywordAnalysisDO>()
                .eq(SeoKeywordAnalysisDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoKeywordAnalysisDO::getAnalysisId, analysisId)
                .orderByAsc(SeoKeywordAnalysisDO::getSort));
    }

    default SeoKeywordAnalysisDO selectByIdAndAnalysisId(Long id, Long analysisId) {
        return selectOne(new LambdaQueryWrapperX<SeoKeywordAnalysisDO>()
                .eq(SeoKeywordAnalysisDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoKeywordAnalysisDO::getId, id)
                .eq(SeoKeywordAnalysisDO::getAnalysisId, analysisId));
    }

}
