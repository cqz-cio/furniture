package cn.iocoder.yudao.module.seo.service.metadata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.dal.mysql.metadata.SeoMetadataMapper;
import cn.iocoder.yudao.module.seo.enums.SeoEntityTypeEnum;
import cn.iocoder.yudao.module.seo.enums.SeoPublishStatusEnum;
import cn.iocoder.yudao.module.seo.service.SeoLocaleUtils;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ENTITY_TYPE_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_CANONICAL_URL_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_DUPLICATE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_IDENTITY_IMMUTABLE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_VERSION_CONFLICT;

@Service
@Validated
public class SeoMetadataServiceImpl implements SeoMetadataService {

    @Resource
    private SeoMetadataMapper metadataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMetadata(SeoMetadataSaveReqVO reqVO) {
        validateEntityType(reqVO.getEntityType());
        SeoMetadataDO metadata = toMetadata(reqVO)
                .setId(null)
                .setPublishStatus(SeoPublishStatusEnum.DRAFT.getCode())
                .setVersion(1);
        metadata.setTenantId(TenantContextHolder.getRequiredTenantId());
        try {
            metadataMapper.insert(metadata);
        } catch (DuplicateKeyException ex) {
            throw exception(METADATA_DUPLICATE);
        }
        return metadata.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMetadata(SeoMetadataSaveReqVO reqVO) {
        if (reqVO.getId() == null || reqVO.getVersion() == null) {
            throw new IllegalArgumentException("id and version are required for update");
        }
        validateEntityType(reqVO.getEntityType());
        SeoMetadataDO existing = getRequiredMetadata(reqVO.getId());
        String normalizedLocale = normalizeLocale(reqVO.getLocale());
        if (!Objects.equals(existing.getSiteId(), reqVO.getSiteId())
                || !Objects.equals(existing.getEntityType(), reqVO.getEntityType())
                || !Objects.equals(existing.getEntityId(), reqVO.getEntityId())
                || !Objects.equals(existing.getLocale(), normalizedLocale)) {
            throw exception(METADATA_IDENTITY_IMMUTABLE);
        }
        SeoMetadataDO update = toMetadata(reqVO);
        int affected = metadataMapper.updateEditableAtomic(update, reqVO.getVersion(), currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(reqVO.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishMetadata(Long id, Integer version) {
        if (id == null || version == null) {
            throw new IllegalArgumentException("id and version are required for publish");
        }
        int affected = metadataMapper.publishAtomic(id, version, currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetadata(Long id) {
        getRequiredMetadata(id);
        metadataMapper.deleteByIdForTenant(id);
    }

    @Override
    public SeoMetadataDO getMetadata(Long id) {
        return getRequiredMetadata(id);
    }

    @Override
    public PageResult<SeoMetadataDO> getMetadataPage(SeoMetadataPageReqVO reqVO) {
        if (StrUtil.isNotBlank(reqVO.getEntityType())) {
            validateEntityType(reqVO.getEntityType());
        }
        if (StrUtil.isNotBlank(reqVO.getLocale())) {
            reqVO.setLocale(normalizeLocale(reqVO.getLocale()));
        }
        PageResult<SeoMetadataDO> page = metadataMapper.selectPage(reqVO);
        page.getList().forEach(SeoMetadataServiceImpl::normalizeResponse);
        return page;
    }

    @Override
    public SeoMetadataDO getPublishedMetadata(Long siteId, String entityType, Long entityId, String locale) {
        validateEntityType(entityType);
        return normalizeResponse(metadataMapper.selectPublished(
                siteId, entityType, entityId, normalizeLocale(locale)));
    }

    private SeoMetadataDO getRequiredMetadata(Long id) {
        SeoMetadataDO metadata = metadataMapper.selectByIdForTenant(id);
        if (metadata == null) {
            throw exception(METADATA_NOT_EXISTS);
        }
        return normalizeResponse(metadata);
    }

    private void classifyAtomicFailure(Long id) {
        getRequiredMetadata(id);
        throw exception(METADATA_VERSION_CONFLICT);
    }

    private static Long currentTenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static String currentUpdater() {
        return Objects.toString(SecurityFrameworkUtils.getLoginUserId(), "");
    }

    private static SeoMetadataDO toMetadata(SeoMetadataSaveReqVO reqVO) {
        return new SeoMetadataDO()
                .setId(reqVO.getId())
                .setSiteId(reqVO.getSiteId())
                .setEntityType(reqVO.getEntityType())
                .setEntityId(reqVO.getEntityId())
                .setLocale(normalizeLocale(reqVO.getLocale()))
                .setSeoTitle(defaultString(reqVO.getSeoTitle()))
                .setMetaDescription(defaultString(reqVO.getMetaDescription()))
                .setFocusKeyphrase(defaultString(reqVO.getFocusKeyphrase()))
                .setRelatedKeyphrases(reqVO.getRelatedKeyphrases() == null ? List.of() : reqVO.getRelatedKeyphrases())
                .setCanonicalUrl(validateCanonicalUrl(reqVO.getCanonicalUrl()))
                .setRobotsIndex(reqVO.getRobotsIndex() == null || reqVO.getRobotsIndex())
                .setRobotsFollow(reqVO.getRobotsFollow() == null || reqVO.getRobotsFollow())
                .setOgTitle(defaultString(reqVO.getOgTitle()))
                .setOgDescription(defaultString(reqVO.getOgDescription()))
                .setOgImage(defaultString(reqVO.getOgImage()))
                .setSchemaType(defaultString(reqVO.getSchemaType()));
    }

    static void validateEntityType(String entityType) {
        if (!SeoEntityTypeEnum.isValid(entityType)) {
            throw exception(ENTITY_TYPE_INVALID);
        }
    }

    static String normalizeLocale(String locale) {
        return SeoLocaleUtils.normalize(locale);
    }

    static String validateCanonicalUrl(String canonicalUrl) {
        if (StrUtil.isBlank(canonicalUrl)) {
            return "";
        }
        String candidate = canonicalUrl.trim();
        if (candidate.contains("\\")) {
            throw exception(METADATA_CANONICAL_URL_INVALID);
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.isOpaque() || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                throw exception(METADATA_CANONICAL_URL_INVALID);
            }
            return candidate;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw exception(METADATA_CANONICAL_URL_INVALID);
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static SeoMetadataDO normalizeResponse(SeoMetadataDO metadata) {
        if (metadata != null && metadata.getRelatedKeyphrases() == null) {
            metadata.setRelatedKeyphrases(List.of());
        }
        return metadata;
    }

}
