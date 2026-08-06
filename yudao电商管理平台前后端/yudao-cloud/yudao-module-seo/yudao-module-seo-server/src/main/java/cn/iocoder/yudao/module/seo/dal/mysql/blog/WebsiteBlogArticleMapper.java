package cn.iocoder.yudao.module.seo.dal.mysql.blog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticlePageReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogArticleDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WebsiteBlogArticleMapper extends BaseMapperX<WebsiteBlogArticleDO> {

    default PageResult<WebsiteBlogArticleDO> selectPage(WebsiteBlogArticlePageReqVO reqVO) {
        LambdaQueryWrapperX<WebsiteBlogArticleDO> wrapper = new LambdaQueryWrapperX<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eqIfPresent(WebsiteBlogArticleDO::getSiteId, reqVO.getSiteId())
                .eqIfPresent(WebsiteBlogArticleDO::getLocale, reqVO.getLocale())
                .eqIfPresent(WebsiteBlogArticleDO::getStatus, reqVO.getStatus());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            wrapper.and(nested -> nested
                    .like(WebsiteBlogArticleDO::getTitle, reqVO.getKeyword())
                    .or().like(WebsiteBlogArticleDO::getSlug, reqVO.getKeyword())
                    .or().like(WebsiteBlogArticleDO::getCategory, reqVO.getKeyword()));
        }
        wrapper.orderByDesc(WebsiteBlogArticleDO::getSortOrder)
                .orderByDesc(WebsiteBlogArticleDO::getPublishedAt)
                .orderByDesc(WebsiteBlogArticleDO::getId);
        return selectPage(reqVO, wrapper);
    }

    default PageResult<WebsiteBlogArticleDO> selectPublishedPage(
            Long siteId, String locale, PageParam pageParam, LocalDateTime now) {
        return selectPage(pageParam, new LambdaQueryWrapperX<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteBlogArticleDO::getSiteId, siteId)
                .eq(WebsiteBlogArticleDO::getLocale, locale)
                .eq(WebsiteBlogArticleDO::getStatus, "PUBLISHED")
                .eq(WebsiteBlogArticleDO::getVisible, true)
                .le(WebsiteBlogArticleDO::getPublishedAt, now)
                .isNotNull(WebsiteBlogArticleDO::getPublishedPayloadJson)
                .orderByDesc(WebsiteBlogArticleDO::getSortOrder)
                .orderByDesc(WebsiteBlogArticleDO::getPublishedAt)
                .orderByDesc(WebsiteBlogArticleDO::getId));
    }

    default WebsiteBlogArticleDO selectByIdForTenant(Long id) {
        return selectOne(new LambdaQueryWrapper<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getId, id)
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    default WebsiteBlogArticleDO selectPublishedBySlug(
            Long siteId, String locale, String slug, LocalDateTime now) {
        return selectOne(new LambdaQueryWrapperX<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteBlogArticleDO::getSiteId, siteId)
                .eq(WebsiteBlogArticleDO::getLocale, locale)
                .eq(WebsiteBlogArticleDO::getPublishedSlug, slug)
                .eq(WebsiteBlogArticleDO::getStatus, "PUBLISHED")
                .eq(WebsiteBlogArticleDO::getVisible, true)
                .le(WebsiteBlogArticleDO::getPublishedAt, now)
                .isNotNull(WebsiteBlogArticleDO::getPublishedPayloadJson));
    }

    default long selectCountByStatus(Long siteId, String locale, String status) {
        return selectCount(new LambdaQueryWrapperX<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(WebsiteBlogArticleDO::getSiteId, siteId)
                .eq(WebsiteBlogArticleDO::getLocale, locale)
                .eqIfPresent(WebsiteBlogArticleDO::getStatus, status));
    }

    default int deleteByIdForTenant(Long id) {
        return delete(new LambdaQueryWrapper<WebsiteBlogArticleDO>()
                .eq(WebsiteBlogArticleDO::getId, id)
                .eq(WebsiteBlogArticleDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    @Update("""
            UPDATE website_blog_article
            SET slug = #{article.slug},
                legacy_path = #{article.legacyPath},
                title = #{article.title},
                title_lines_json = #{article.titleLinesJson},
                category = #{article.category},
                label = #{article.label},
                summary = #{article.summary},
                cover_image_url = #{article.coverImageUrl},
                cover_image_alt = #{article.coverImageAlt},
                hero_image_url = #{article.heroImageUrl},
                sections_json = #{article.sectionsJson},
                visible = #{article.visible},
                published_at = #{article.publishedAt},
                sort_order = #{article.sortOrder},
                seo_title = #{article.seoTitle},
                seo_description = #{article.seoDescription},
                version = version + 1,
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{article.id}
              AND version = #{expectedVersion}
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int updateEditableAtomic(@Param("article") WebsiteBlogArticleDO article,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("tenantId") Long tenantId,
                             @Param("updater") String updater);

    @Update("""
            UPDATE website_blog_article
            SET status = 'PUBLISHED',
                published_at = #{publishedAt},
                published_slug = #{publishedSlug},
                published_payload_json = #{payloadJson},
                published_version = version + 1,
                last_published_time = CURRENT_TIMESTAMP,
                published_by = #{updater},
                version = version + 1,
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int publishAtomic(@Param("id") Long id,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("publishedAt") LocalDateTime publishedAt,
                      @Param("publishedSlug") String publishedSlug,
                      @Param("payloadJson") String payloadJson,
                      @Param("tenantId") Long tenantId,
                      @Param("updater") String updater);

    @Update("""
            UPDATE website_blog_article
            SET status = 'OFFLINE',
                version = version + 1,
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int offlineAtomic(@Param("id") Long id,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("tenantId") Long tenantId,
                      @Param("updater") String updater);

}
