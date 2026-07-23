package cn.iocoder.yudao.module.seo.dal.mysql.analysis;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.analysis.SeoAnalysisItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SeoAnalysisItemMapper extends BaseMapperX<SeoAnalysisItemDO> {

    default List<SeoAnalysisItemDO> selectListByAnalysisId(Long analysisId) {
        return selectList(new LambdaQueryWrapperX<SeoAnalysisItemDO>()
                .eq(SeoAnalysisItemDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoAnalysisItemDO::getAnalysisId, analysisId)
                .orderByAsc(SeoAnalysisItemDO::getSort));
    }

}
