package cn.iocoder.yudao.module.seo.dal.mysql.code;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.code.WebsiteCodeConfigDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebsiteCodeConfigMapper extends BaseMapperX<WebsiteCodeConfigDO> {
    default WebsiteCodeConfigDO selectSite(Long siteId, boolean lock) {
        LambdaQueryWrapper<WebsiteCodeConfigDO> query = new LambdaQueryWrapper<WebsiteCodeConfigDO>()
            .eq(WebsiteCodeConfigDO::getTenantId, TenantContextHolder.getRequiredTenantId())
            .eq(WebsiteCodeConfigDO::getSiteId, siteId);
        if (lock) query.last("FOR UPDATE");
        return selectOne(query);
    }
}
