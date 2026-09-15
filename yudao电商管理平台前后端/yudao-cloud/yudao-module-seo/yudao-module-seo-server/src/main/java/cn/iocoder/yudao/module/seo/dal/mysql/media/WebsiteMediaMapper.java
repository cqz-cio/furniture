package cn.iocoder.yudao.module.seo.dal.mysql.media;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.media.vo.WebsiteMediaPageReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.media.WebsiteMediaDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface WebsiteMediaMapper extends BaseMapperX<WebsiteMediaDO> {
    default PageResult<WebsiteMediaDO> page(WebsiteMediaPageReqVO request) {
        return selectPage(request, new LambdaQueryWrapperX<WebsiteMediaDO>()
            .eq(WebsiteMediaDO::getTenantId, TenantContextHolder.getRequiredTenantId())
            .eq(WebsiteMediaDO::getArchived, Boolean.TRUE.equals(request.getArchived()))
            .likeIfPresent(WebsiteMediaDO::getName, request.getName())
            .eqIfPresent(WebsiteMediaDO::getKind, request.getKind())
            .orderByDesc(WebsiteMediaDO::getId));
    }
}
