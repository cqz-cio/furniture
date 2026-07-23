package cn.iocoder.yudao.module.seo.dal.mysql.analysis;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoAnalysisDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SeoAnalysisMapper extends BaseMapperX<SeoAnalysisDO> {

    default SeoAnalysisDO selectByIdForTenant(Long id) {
        return selectOne(new LambdaQueryWrapperX<SeoAnalysisDO>()
                .eq(SeoAnalysisDO::getId, id)
                .eq(SeoAnalysisDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    default SeoAnalysisDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<SeoAnalysisDO>()
                .eq(SeoAnalysisDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoAnalysisDO::getIdempotencyKey, idempotencyKey));
    }

    default List<SeoAnalysisDO> selectEntityHistory(Long siteId, String entityType, Long entityId, String locale) {
        return selectList(new LambdaQueryWrapperX<SeoAnalysisDO>()
                .eq(SeoAnalysisDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoAnalysisDO::getSiteId, siteId)
                .eq(SeoAnalysisDO::getEntityType, entityType)
                .eq(SeoAnalysisDO::getEntityId, entityId)
                .eq(SeoAnalysisDO::getLocale, locale)
                .orderByDesc(SeoAnalysisDO::getId));
    }

}
