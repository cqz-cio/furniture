package cn.iocoder.yudao.module.seo.dal.mysql.metadata;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.enums.SeoPublishStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeoMetadataMapper extends BaseMapperX<SeoMetadataDO> {

    default SeoMetadataDO selectByEntity(Long siteId, String entityType, Long entityId, String locale) {
        return selectOne(new LambdaQueryWrapperX<SeoMetadataDO>()
                .eq(SeoMetadataDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoMetadataDO::getSiteId, siteId)
                .eq(SeoMetadataDO::getEntityType, entityType)
                .eq(SeoMetadataDO::getEntityId, entityId)
                .eq(SeoMetadataDO::getLocale, locale));
    }

    default SeoMetadataDO selectPublished(Long siteId, String entityType, Long entityId, String locale) {
        return selectOne(new LambdaQueryWrapperX<SeoMetadataDO>()
                .eq(SeoMetadataDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eq(SeoMetadataDO::getSiteId, siteId)
                .eq(SeoMetadataDO::getEntityType, entityType)
                .eq(SeoMetadataDO::getEntityId, entityId)
                .eq(SeoMetadataDO::getLocale, locale)
                .eq(SeoMetadataDO::getPublishStatus, SeoPublishStatusEnum.PUBLISHED.getCode()));
    }

    default PageResult<SeoMetadataDO> selectPage(SeoMetadataPageReqVO reqVO) {
        LambdaQueryWrapperX<SeoMetadataDO> wrapper = new LambdaQueryWrapperX<SeoMetadataDO>()
                .eq(SeoMetadataDO::getTenantId, TenantContextHolder.getRequiredTenantId())
                .eqIfPresent(SeoMetadataDO::getSiteId, reqVO.getSiteId())
                .eqIfPresent(SeoMetadataDO::getEntityType, reqVO.getEntityType())
                .eqIfPresent(SeoMetadataDO::getEntityId, reqVO.getEntityId())
                .eqIfPresent(SeoMetadataDO::getLocale, reqVO.getLocale())
                .eqIfPresent(SeoMetadataDO::getPublishStatus, reqVO.getPublishStatus());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            wrapper.and(nested -> nested
                    .like(SeoMetadataDO::getSeoTitle, reqVO.getKeyword())
                    .or().like(SeoMetadataDO::getMetaDescription, reqVO.getKeyword())
                    .or().like(SeoMetadataDO::getFocusKeyphrase, reqVO.getKeyword()));
        }
        wrapper.orderByDesc(SeoMetadataDO::getId);
        return selectPage(reqVO, wrapper);
    }

    default SeoMetadataDO selectByIdForTenant(Long id) {
        return selectOne(new LambdaQueryWrapper<SeoMetadataDO>()
                .eq(SeoMetadataDO::getId, id)
                .eq(SeoMetadataDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    default int deleteByIdForTenant(Long id) {
        return delete(new LambdaQueryWrapper<SeoMetadataDO>()
                .eq(SeoMetadataDO::getId, id)
                .eq(SeoMetadataDO::getTenantId, TenantContextHolder.getRequiredTenantId()));
    }

    @Update("""
            UPDATE seo_metadata
            SET seo_title = #{metadata.seoTitle},
                meta_description = #{metadata.metaDescription},
                focus_keyphrase = #{metadata.focusKeyphrase},
                related_keyphrases = #{metadata.relatedKeyphrases,
                    typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                canonical_url = #{metadata.canonicalUrl},
                robots_index = #{metadata.robotsIndex},
                robots_follow = #{metadata.robotsFollow},
                og_title = #{metadata.ogTitle},
                og_description = #{metadata.ogDescription},
                og_image = #{metadata.ogImage},
                schema_type = #{metadata.schemaType},
                version = version + 1,
                updater = #{updater},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{metadata.id}
              AND version = #{expectedVersion}
              AND tenant_id = #{tenantId}
              AND deleted = FALSE
            """)
    int updateEditableAtomic(@Param("metadata") SeoMetadataDO metadata,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("tenantId") Long tenantId,
                             @Param("updater") String updater);

    @Update("""
            UPDATE seo_metadata
            SET publish_status = 'PUBLISHED',
                published_time = CURRENT_TIMESTAMP,
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
                      @Param("tenantId") Long tenantId,
                      @Param("updater") String updater);

}
