package cn.iocoder.yudao.module.seo.dal.mysql.blog;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogPublishRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WebsiteBlogPublishRecordMapper extends BaseMapperX<WebsiteBlogPublishRecordDO> {

    default List<WebsiteBlogPublishRecordDO> selectListByArticleId(Long articleId) {
        return selectList(new LambdaQueryWrapperX<WebsiteBlogPublishRecordDO>()
                .eq(WebsiteBlogPublishRecordDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteBlogPublishRecordDO::getArticleId, articleId)
                .orderByDesc(WebsiteBlogPublishRecordDO::getPublishedVersion)
                .last("LIMIT 20"));
    }

}
