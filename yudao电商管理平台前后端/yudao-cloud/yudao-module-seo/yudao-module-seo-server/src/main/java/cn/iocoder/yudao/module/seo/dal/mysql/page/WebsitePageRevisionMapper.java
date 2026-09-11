package cn.iocoder.yudao.module.seo.dal.mysql.page;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.page.WebsitePageRevisionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface WebsitePageRevisionMapper extends BaseMapperX<WebsitePageRevisionDO> {
    default WebsitePageRevisionDO selectRevision(Long pageId, Long id) {
        return selectOne(new LambdaQueryWrapper<WebsitePageRevisionDO>()
                .eq(WebsitePageRevisionDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsitePageRevisionDO::getPageId, pageId).eq(WebsitePageRevisionDO::getId, id));
    }
    default List<WebsitePageRevisionDO> selectHistory(Long pageId) {
        return selectList(new LambdaQueryWrapper<WebsitePageRevisionDO>()
                .eq(WebsitePageRevisionDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsitePageRevisionDO::getPageId, pageId)
                .orderByDesc(WebsitePageRevisionDO::getId).last("LIMIT 50"));
    }
}
