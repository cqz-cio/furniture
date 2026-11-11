package cn.iocoder.yudao.module.seo.dal.mysql.operations;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.operations.WebsiteDocumentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface WebsiteDocumentMapper extends BaseMapperX<WebsiteDocumentDO> {
    default LambdaQueryWrapper<WebsiteDocumentDO> scope(Long siteId) { return new LambdaQueryWrapper<WebsiteDocumentDO>().eq(WebsiteDocumentDO::getTenantId,TenantContextHolder.getRequiredTenantId()).eq(WebsiteDocumentDO::getSiteId,siteId); }
    default List<WebsiteDocumentDO> documents(Long siteId) { return selectList(scope(siteId).orderByDesc(WebsiteDocumentDO::getId)); }
    default WebsiteDocumentDO get(Long siteId,Long id,boolean lock) { var q=scope(siteId).eq(WebsiteDocumentDO::getId,id); if(lock)q.last("FOR UPDATE");return selectOne(q); }
}
