package cn.iocoder.yudao.module.seo.dal.mysql.navigation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WebsiteNavigationItemMapper extends BaseMapperX<WebsiteNavigationItemDO> {

    default List<WebsiteNavigationItemDO> selectListByRevisionId(Long revisionId) {
        return selectList(new LambdaQueryWrapperX<WebsiteNavigationItemDO>()
                .eq(WebsiteNavigationItemDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteNavigationItemDO::getRevisionId, revisionId)
                .orderByAsc(WebsiteNavigationItemDO::getSort)
                .orderByAsc(WebsiteNavigationItemDO::getId));
    }

    default int deleteByRevisionId(Long revisionId) {
        return delete(new LambdaQueryWrapper<WebsiteNavigationItemDO>()
                .eq(WebsiteNavigationItemDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteNavigationItemDO::getRevisionId, revisionId));
    }

}
