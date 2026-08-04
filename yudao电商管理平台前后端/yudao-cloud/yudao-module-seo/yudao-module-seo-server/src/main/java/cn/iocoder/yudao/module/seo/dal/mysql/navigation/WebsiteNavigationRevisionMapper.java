package cn.iocoder.yudao.module.seo.dal.mysql.navigation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationRevisionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebsiteNavigationRevisionMapper extends BaseMapperX<WebsiteNavigationRevisionDO> {

    default WebsiteNavigationRevisionDO selectActive(Long siteId, String locale, String status) {
        return selectOne(new LambdaQueryWrapperX<WebsiteNavigationRevisionDO>()
                .eq(WebsiteNavigationRevisionDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteNavigationRevisionDO::getSiteId, siteId)
                .eq(WebsiteNavigationRevisionDO::getLocale, locale)
                .eq(WebsiteNavigationRevisionDO::getStatus, status));
    }

    default WebsiteNavigationRevisionDO selectByIdForTenant(Long id) {
        return selectOne(new LambdaQueryWrapper<WebsiteNavigationRevisionDO>()
                .eq(WebsiteNavigationRevisionDO::getId, id)
                .eq(WebsiteNavigationRevisionDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    @Select("""
            SELECT COALESCE(MAX(revision_no), 0)
            FROM website_navigation_revision
            WHERE tenant_id = #{tenantId}
              AND site_id = #{siteId}
              AND locale = #{locale}
              AND deleted = FALSE
            """)
    Integer selectMaxRevisionNo(@Param("tenantId") Long tenantId,
                                @Param("siteId") Long siteId,
                                @Param("locale") String locale);

    @Update("""
            UPDATE website_navigation_revision
            SET version = version + 1,
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND status = 'DRAFT'
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int bumpDraftVersionAtomic(@Param("id") Long id,
                               @Param("expectedVersion") Integer expectedVersion,
                               @Param("tenantId") Long tenantId,
                               @Param("updater") String updater);

    @Update("""
            UPDATE website_navigation_revision
            SET status = 'ARCHIVED',
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE site_id = #{siteId}
              AND locale = #{locale}
              AND status = 'PUBLISHED'
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int archivePublished(@Param("siteId") Long siteId,
                         @Param("locale") String locale,
                         @Param("tenantId") Long tenantId,
                         @Param("updater") String updater);

    @Update("""
            UPDATE website_navigation_revision
            SET status = 'PUBLISHED',
                version = version + 1,
                published_time = CURRENT_TIMESTAMP,
                published_by = #{updater},
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND status = 'DRAFT'
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int publishDraftAtomic(@Param("id") Long id,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("tenantId") Long tenantId,
                           @Param("updater") String updater);

}
