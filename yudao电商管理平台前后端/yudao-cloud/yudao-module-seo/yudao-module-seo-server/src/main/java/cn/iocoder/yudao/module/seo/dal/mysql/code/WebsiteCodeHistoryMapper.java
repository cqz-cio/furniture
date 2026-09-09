package cn.iocoder.yudao.module.seo.dal.mysql.code;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.code.WebsiteCodeHistoryDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface WebsiteCodeHistoryMapper extends BaseMapperX<WebsiteCodeHistoryDO> {
    default LambdaQueryWrapper<WebsiteCodeHistoryDO> siteQuery(Long siteId) {
        return new LambdaQueryWrapper<WebsiteCodeHistoryDO>()
            .eq(WebsiteCodeHistoryDO::getTenantId, TenantContextHolder.getRequiredTenantId())
            .eq(WebsiteCodeHistoryDO::getSiteId, siteId);
    }
    default List<WebsiteCodeHistoryDO> selectHistory(Long siteId) {
        return selectList(siteQuery(siteId).orderByDesc(WebsiteCodeHistoryDO::getVersion).last("LIMIT 50"));
    }
    default WebsiteCodeHistoryDO selectSiteRecord(Long siteId, Long id) {
        return selectOne(siteQuery(siteId).eq(WebsiteCodeHistoryDO::getId, id));
    }
}
