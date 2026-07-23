package cn.iocoder.yudao.module.seo.dal.mysql.analysis;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoKeywordAnalysisItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SeoKeywordAnalysisItemMapper extends BaseMapperX<SeoKeywordAnalysisItemDO> {

    default List<SeoKeywordAnalysisItemDO> selectListByKeywordAnalysisId(Long keywordAnalysisId) {
        return selectList(new LambdaQueryWrapperX<SeoKeywordAnalysisItemDO>()
                .eq(SeoKeywordAnalysisItemDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoKeywordAnalysisItemDO::getKeywordAnalysisId, keywordAnalysisId)
                .orderByAsc(SeoKeywordAnalysisItemDO::getSort));
    }

}
