package cn.iocoder.yudao.module.seo.dal.mysql.page;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.page.WebsitePageDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebsitePageMapper extends BaseMapperX<WebsitePageDO> {
    default WebsitePageDO selectPage(Long siteId, String pageKey, String locale, boolean lock) {
        var query = new LambdaQueryWrapper<WebsitePageDO>()
                .eq(WebsitePageDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsitePageDO::getSiteId, siteId)
                .eq(WebsitePageDO::getPageKey, pageKey)
                .eq(WebsitePageDO::getLocale, locale);
        if (lock) query.last("FOR UPDATE");
        return selectOne(query);
    }
}
