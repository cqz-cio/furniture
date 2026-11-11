package cn.iocoder.yudao.module.seo.dal.mysql.operations;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.operations.WebsitePrivateFileDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface WebsitePrivateFileMapper extends BaseMapperX<WebsitePrivateFileDO> {
    default LambdaQueryWrapper<WebsitePrivateFileDO> scope(Long siteId) { return new LambdaQueryWrapper<WebsitePrivateFileDO>().eq(WebsitePrivateFileDO::getTenantId,TenantContextHolder.getRequiredTenantId()).eq(WebsitePrivateFileDO::getSiteId,siteId); }
    default WebsitePrivateFileDO get(Long siteId,Long id) { return selectOne(scope(siteId).eq(WebsitePrivateFileDO::getId,id)); }
    @Select("SELECT id, content FROM website_private_file WHERE id=#{id} AND site_id=#{siteId} AND tenant_id=#{tenantId} AND deleted=0")
    WebsitePrivateFileDO withContent(@Param("tenantId") Long tenantId,@Param("siteId") Long siteId,@Param("id") Long id);
    @Delete("DELETE FROM website_private_file WHERE id=#{id} AND site_id=#{siteId} AND tenant_id=#{tenantId} AND deleted=0")
    int deleteUnused(@Param("tenantId") Long tenantId,@Param("siteId") Long siteId,@Param("id") Long id);
}
