package cn.iocoder.yudao.module.seo.service.blog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticlePageReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPublishRecordRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSectionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSectionSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSummaryRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogVersionReqVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogCoverImageRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPageRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPreviewSessionRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogSectionRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogArticleDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogPublishRecordDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogArticleMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogPublishRecordMapper;
import cn.iocoder.yudao.module.seo.dal.redis.blog.WebsiteBlogPreviewGrant;
import cn.iocoder.yudao.module.seo.dal.redis.blog.WebsiteBlogPreviewRedisDAO;
import cn.iocoder.yudao.module.seo.enums.blog.WebsiteBlogStatusEnum;
import cn.iocoder.yudao.module.seo.service.SeoLocaleUtils;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_DUPLICATE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_IDENTITY_IMMUTABLE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_ARTICLE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_PREVIEW_EXPIRED;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.BLOG_PREVIEW_ORIGIN_MISMATCH;

@Service
@Validated
public class WebsiteBlogServiceImpl implements WebsiteBlogService {

    private static final Duration PREVIEW_TICKET_TTL = Duration.ofMinutes(10);
    private static final Duration PREVIEW_SESSION_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final int WORDS_PER_MINUTE = 180;

    @Resource
    private WebsiteBlogArticleMapper websiteBlogArticleMapper;
    @Resource
    private WebsiteBlogPublishRecordMapper publishRecordMapper;
    @Resource
    private WebsiteBlogPreviewRedisDAO previewRedisDAO;
    @Resource
    private SeoSiteConfigService siteConfigService;

    @Override
    public PageResult<WebsiteBlogArticleRespVO> getArticlePage(WebsiteBlogArticlePageReqVO reqVO) {
        reqVO.setLocale(SeoLocaleUtils.normalize(reqVO.getLocale()));
        if (StrUtil.isNotBlank(reqVO.getStatus())) {
            String normalizedStatus = reqVO.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!WebsiteBlogStatusEnum.isValid(normalizedStatus)) {
                throw exception(BLOG_ARTICLE_INVALID);
            }
            reqVO.setStatus(normalizedStatus);
        }
        PageResult<WebsiteBlogArticleDO> page = websiteBlogArticleMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toAdminResponse).toList(), page.getTotal());
    }

    @Override
    public WebsiteBlogSummaryRespVO getSummary(Long siteId, String locale) {
        String normalizedLocale = SeoLocaleUtils.normalize(locale);
        long total = websiteBlogArticleMapper.selectCountByStatus(siteId, normalizedLocale, null);
        long draft = websiteBlogArticleMapper.selectCountByStatus(siteId, normalizedLocale,
                WebsiteBlogStatusEnum.DRAFT.getCode());
        long published = websiteBlogArticleMapper.selectCountByStatus(siteId, normalizedLocale,
                WebsiteBlogStatusEnum.PUBLISHED.getCode());
        long offline = websiteBlogArticleMapper.selectCountByStatus(siteId, normalizedLocale,
                WebsiteBlogStatusEnum.OFFLINE.getCode());
        return new WebsiteBlogSummaryRespVO(total, draft, published, offline);
    }

    @Override
    public WebsiteBlogArticleRespVO getArticle(Long id) {
        return toAdminResponse(getRequiredArticle(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArticle(WebsiteBlogArticleSaveReqVO reqVO) {
        siteConfigService.getRequiredSiteConfig(reqVO.getSiteId());
        WebsiteBlogArticleDO article = toEditableArticle(reqVO)
                .setId(null)
                .setStatus(WebsiteBlogStatusEnum.DRAFT.getCode())
                .setVersion(1)
                .setPublishedVersion(null)
                .setPublishedSlug("")
                .setPublishedPayloadJson(null)
                .setPublishedBy("");
        article.setTenantId(currentTenantId());
        try {
            websiteBlogArticleMapper.insert(article);
        } catch (DuplicateKeyException ex) {
            throw exception(BLOG_ARTICLE_DUPLICATE);
        }
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(WebsiteBlogArticleSaveReqVO reqVO) {
        if (reqVO.getId() == null || reqVO.getVersion() == null) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
        WebsiteBlogArticleDO existing = getRequiredArticle(reqVO.getId());
        String normalizedLocale = SeoLocaleUtils.normalize(reqVO.getLocale());
        if (!Objects.equals(existing.getSiteId(), reqVO.getSiteId())
                || !Objects.equals(existing.getLocale(), normalizedLocale)) {
            throw exception(BLOG_ARTICLE_IDENTITY_IMMUTABLE);
        }
        WebsiteBlogArticleDO update = toEditableArticle(reqVO).setId(existing.getId());
        try {
            int affected = websiteBlogArticleMapper.updateEditableAtomic(update, reqVO.getVersion(),
                    currentTenantId(), currentUpdater());
            if (affected == 0) {
                classifyAtomicFailure(existing.getId());
            }
        } catch (DuplicateKeyException ex) {
            throw exception(BLOG_ARTICLE_DUPLICATE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id) {
        getRequiredArticle(id);
        websiteBlogArticleMapper.deleteByIdForTenant(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishArticle(WebsiteBlogVersionReqVO reqVO) {
        WebsiteBlogArticleDO article = getRequiredArticle(reqVO.getId());
        if (!Objects.equals(article.getVersion(), reqVO.getVersion())) {
            throw exception(BLOG_ARTICLE_VERSION_CONFLICT);
        }
        List<WebsiteBlogSectionRespVO> sections = parseSections(article.getSectionsJson());
        validatePublishable(article, sections);
        LocalDateTime effectivePublishedAt = article.getPublishedAt() == null
                ? LocalDateTime.now() : article.getPublishedAt();
        AppWebsiteBlogArticleRespVO payload = buildPublicResponse(article, sections, effectivePublishedAt);
        String payloadJson = JsonUtils.toJsonString(payload);
        String updater = currentUpdater();
        try {
            int affected = websiteBlogArticleMapper.publishAtomic(article.getId(), article.getVersion(),
                    effectivePublishedAt, article.getSlug(), payloadJson, currentTenantId(), updater);
            if (affected == 0) {
                classifyAtomicFailure(article.getId());
            }
        } catch (DuplicateKeyException ex) {
            throw exception(BLOG_ARTICLE_DUPLICATE);
        }
        WebsiteBlogPublishRecordDO record = new WebsiteBlogPublishRecordDO()
                .setArticleId(article.getId())
                .setPublishedVersion(article.getVersion() + 1)
                .setSlug(article.getSlug())
                .setTitle(article.getTitle())
                .setPublishedAt(effectivePublishedAt)
                .setPublishedBy(updater)
                .setSnapshotJson(payloadJson);
        record.setTenantId(currentTenantId());
        publishRecordMapper.insert(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offlineArticle(WebsiteBlogVersionReqVO reqVO) {
        getRequiredArticle(reqVO.getId());
        int affected = websiteBlogArticleMapper.offlineAtomic(reqVO.getId(), reqVO.getVersion(),
                currentTenantId(), currentUpdater());
        if (affected == 0) {
            classifyAtomicFailure(reqVO.getId());
        }
    }

    @Override
    public List<WebsiteBlogPublishRecordRespVO> getPublishHistory(Long articleId) {
        getRequiredArticle(articleId);
        return publishRecordMapper.selectListByArticleId(articleId).stream().map(record -> {
            WebsiteBlogPublishRecordRespVO response = new WebsiteBlogPublishRecordRespVO();
            response.setId(record.getId());
            response.setPublishedVersion(record.getPublishedVersion());
            response.setSlug(record.getSlug());
            response.setTitle(record.getTitle());
            response.setPublishedAt(record.getPublishedAt());
            response.setPublishedBy(record.getPublishedBy());
            response.setCreateTime(record.getCreateTime());
            return response;
        }).toList();
    }

    @Override
    public WebsiteBlogPreviewTicketRespVO createPreviewTicket(WebsiteBlogVersionReqVO reqVO) {
        WebsiteBlogArticleDO article = getRequiredArticle(reqVO.getId());
        if (!Objects.equals(article.getVersion(), reqVO.getVersion())) {
            throw exception(BLOG_ARTICLE_VERSION_CONFLICT);
        }
        SeoSiteConfigDO siteConfig = siteConfigService.getRequiredSiteConfig(article.getSiteId());
        String previewOrigin = originOf(siteConfig.getSiteUrl());
        WebsiteBlogPreviewGrant grant = new WebsiteBlogPreviewGrant(
                currentTenantId(), article.getSiteId(), article.getLocale(), article.getId(),
                article.getVersion(), previewOrigin);
        String ticket = randomToken("bpv_");
        previewRedisDAO.setTicket(ticket, grant, PREVIEW_TICKET_TTL);
        String previewUrl = siteConfig.getSiteUrl() + "/preview/blog#ticket=" + ticket
                + "&tenantId=" + currentTenantId();
        return new WebsiteBlogPreviewTicketRespVO(previewUrl, (int) PREVIEW_TICKET_TTL.toSeconds());
    }

    @Override
    public AppWebsiteBlogPageRespVO getPublishedPage(
            Long siteId, String locale, Integer page, Integer pageSize) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(page);
        pageParam.setPageSize(pageSize);
        PageResult<WebsiteBlogArticleDO> result = websiteBlogArticleMapper.selectPublishedPage(
                siteId, SeoLocaleUtils.normalize(locale), pageParam, LocalDateTime.now());
        List<AppWebsiteBlogArticleRespVO> items = result.getList().stream()
                .map(this::parsePublishedPayload)
                .filter(Objects::nonNull)
                .toList();
        return new AppWebsiteBlogPageRespVO(items, result.getTotal(), page, pageSize);
    }

    @Override
    public AppWebsiteBlogArticleRespVO getPublishedArticle(
            Long siteId, String locale, String slug) {
        WebsiteBlogArticleDO article = websiteBlogArticleMapper.selectPublishedBySlug(
                siteId, SeoLocaleUtils.normalize(locale), slug, LocalDateTime.now());
        return article == null ? null : parsePublishedPayload(article);
    }

    @Override
    public AppWebsiteBlogPreviewSessionRespVO exchangePreviewTicket(
            String ticket, String requestOrigin) {
        WebsiteBlogPreviewGrant grant = previewRedisDAO.consumeTicket(ticket);
        verifyPreviewGrant(grant, requestOrigin);
        String session = randomToken("bps_");
        previewRedisDAO.setSession(session, grant, PREVIEW_SESSION_TTL);
        return new AppWebsiteBlogPreviewSessionRespVO(session,
                (int) PREVIEW_SESSION_TTL.toSeconds());
    }

    @Override
    public AppWebsiteBlogArticleRespVO getPreview(String session, String requestOrigin) {
        WebsiteBlogPreviewGrant grant = previewRedisDAO.getSession(session);
        WebsiteBlogArticleDO article = verifyPreviewGrant(grant, requestOrigin);
        LocalDateTime effectivePublishedAt = article.getPublishedAt() == null
                ? LocalDateTime.now() : article.getPublishedAt();
        return buildPublicResponse(article, parseSections(article.getSectionsJson()), effectivePublishedAt);
    }

    private WebsiteBlogArticleDO toEditableArticle(WebsiteBlogArticleSaveReqVO reqVO) {
        String slug = trim(reqVO.getSlug()).toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
        String legacyPath = normalizeLegacyPath(reqVO.getLegacyPath());
        String coverImageUrl = normalizeImageUrl(reqVO.getCoverImageUrl(), true);
        String heroImageUrl = normalizeImageUrl(reqVO.getHeroImageUrl(), false);
        String title = trim(reqVO.getTitle());
        String summary = trim(reqVO.getSummary());
        List<String> titleLines = normalizeTitleLines(reqVO.getTitleLines(), title);
        List<WebsiteBlogSectionRespVO> sections = normalizeSections(reqVO.getSections());
        int sortOrder = reqVO.getSortOrder() == null ? 0 : reqVO.getSortOrder();
        if (sortOrder < -100000 || sortOrder > 100000) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
        return new WebsiteBlogArticleDO()
                .setSiteId(reqVO.getSiteId())
                .setLocale(SeoLocaleUtils.normalize(reqVO.getLocale()))
                .setSlug(slug)
                .setLegacyPath(legacyPath)
                .setTitle(title)
                .setTitleLinesJson(JsonUtils.toJsonString(titleLines))
                .setCategory(trim(reqVO.getCategory()))
                .setLabel(trim(reqVO.getLabel()))
                .setSummary(summary)
                .setCoverImageUrl(coverImageUrl)
                .setCoverImageAlt(trim(reqVO.getCoverImageAlt()))
                .setHeroImageUrl(heroImageUrl)
                .setSectionsJson(JsonUtils.toJsonString(sections))
                .setVisible(reqVO.getVisible() == null || reqVO.getVisible())
                .setPublishedAt(reqVO.getPublishedAt())
                .setSortOrder(sortOrder)
                .setSeoTitle(StrUtil.blankToDefault(trim(reqVO.getSeoTitle()), title + " — VANZ Journal"))
                .setSeoDescription(StrUtil.blankToDefault(trim(reqVO.getSeoDescription()), summary));
    }

    private WebsiteBlogArticleRespVO toAdminResponse(WebsiteBlogArticleDO article) {
        List<WebsiteBlogSectionRespVO> sections = parseSections(article.getSectionsJson());
        WebsiteBlogArticleRespVO response = new WebsiteBlogArticleRespVO();
        response.setId(article.getId());
        response.setSiteId(article.getSiteId());
        response.setLocale(article.getLocale());
        response.setSlug(article.getSlug());
        response.setLegacyPath(article.getLegacyPath());
        response.setTitle(article.getTitle());
        response.setTitleLines(parseTitleLines(article.getTitleLinesJson(), article.getTitle()));
        response.setCategory(article.getCategory());
        response.setLabel(article.getLabel());
        response.setSummary(article.getSummary());
        response.setCoverImageUrl(article.getCoverImageUrl());
        response.setCoverImageAlt(article.getCoverImageAlt());
        response.setHeroImageUrl(StrUtil.blankToDefault(article.getHeroImageUrl(), article.getCoverImageUrl()));
        response.setSections(sections);
        response.setStatus(article.getStatus());
        response.setVisible(article.getVisible());
        response.setPublishedAt(article.getPublishedAt());
        response.setSortOrder(article.getSortOrder());
        response.setSeoTitle(article.getSeoTitle());
        response.setSeoDescription(article.getSeoDescription());
        response.setVersion(article.getVersion());
        response.setPublishedVersion(article.getPublishedVersion());
        response.setHasUnpublishedChanges(article.getPublishedVersion() != null
                && !Objects.equals(article.getPublishedVersion(), article.getVersion()));
        response.setReadTime(readTime(sections, article.getSummary()));
        response.setLastPublishedTime(article.getLastPublishedTime());
        response.setPublishedBy(article.getPublishedBy());
        response.setCreateTime(article.getCreateTime());
        response.setUpdateTime(article.getUpdateTime());
        return response;
    }

    private AppWebsiteBlogArticleRespVO buildPublicResponse(
            WebsiteBlogArticleDO article, List<WebsiteBlogSectionRespVO> sections,
            LocalDateTime publishedAt) {
        AppWebsiteBlogArticleRespVO response = new AppWebsiteBlogArticleRespVO();
        response.setId(article.getId());
        response.setSlug(article.getSlug());
        response.setPath(StrUtil.isNotBlank(article.getLegacyPath())
                ? article.getLegacyPath() : "/blog/" + article.getSlug());
        response.setTitle(article.getTitle());
        response.setTitleLines(parseTitleLines(article.getTitleLinesJson(), article.getTitle()));
        response.setCategory(article.getCategory());
        response.setLabel(article.getLabel());
        response.setSummary(article.getSummary());
        response.setCoverImage(new AppWebsiteBlogCoverImageRespVO(
                article.getCoverImageUrl(), article.getCoverImageAlt()));
        response.setHeroImage(StrUtil.blankToDefault(article.getHeroImageUrl(), article.getCoverImageUrl()));
        response.setPublishedAt(publishedAt.toString());
        response.setDisplayDate(DISPLAY_DATE_FORMATTER.format(publishedAt));
        response.setReadTime(readTime(sections, article.getSummary()));
        response.setSortOrder(article.getSortOrder());
        response.setSections(sections.stream().map(section -> {
            AppWebsiteBlogSectionRespVO publicSection = new AppWebsiteBlogSectionRespVO();
            publicSection.setId(section.getId());
            publicSection.setNumber(section.getNumber());
            publicSection.setTitle(section.getTitle());
            publicSection.setParagraphs(section.getParagraphs());
            return publicSection;
        }).toList());
        response.setSeoTitle(article.getSeoTitle());
        response.setSeoDescription(article.getSeoDescription());
        return response;
    }

    private AppWebsiteBlogArticleRespVO parsePublishedPayload(WebsiteBlogArticleDO article) {
        if (StrUtil.isBlank(article.getPublishedPayloadJson())) {
            return null;
        }
        return JsonUtils.parseObject(article.getPublishedPayloadJson(), AppWebsiteBlogArticleRespVO.class);
    }

    private static List<String> normalizeTitleLines(List<String> values, String title) {
        if (values == null || values.isEmpty()) {
            return List.of(title);
        }
        List<String> lines = values.stream().map(WebsiteBlogServiceImpl::trim)
                .filter(StrUtil::isNotBlank).toList();
        return lines.isEmpty() ? List.of(title) : lines;
    }

    private static List<WebsiteBlogSectionRespVO> normalizeSections(
            List<WebsiteBlogSectionSaveReqVO> requestSections) {
        if (requestSections == null || requestSections.isEmpty()) {
            return List.of();
        }
        List<WebsiteBlogSectionRespVO> sections = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (WebsiteBlogSectionSaveReqVO requestSection : requestSections) {
            String title = trim(requestSection.getTitle());
            List<String> paragraphs = requestSection.getParagraphs() == null ? List.of()
                    : requestSection.getParagraphs().stream().map(WebsiteBlogServiceImpl::trim)
                    .filter(StrUtil::isNotBlank).toList();
            if (StrUtil.isBlank(title) && paragraphs.isEmpty()) {
                continue;
            }
            if (StrUtil.isBlank(title) || paragraphs.isEmpty()) {
                throw exception(BLOG_ARTICLE_INVALID);
            }
            String baseId = slugify(StrUtil.blankToDefault(requestSection.getId(), title));
            if (StrUtil.isBlank(baseId)) {
                throw exception(BLOG_ARTICLE_INVALID);
            }
            String id = baseId;
            int suffix = 2;
            while (!ids.add(id)) {
                id = baseId + "-" + suffix++;
            }
            WebsiteBlogSectionRespVO section = new WebsiteBlogSectionRespVO();
            section.setId(id);
            section.setNumber(String.format(Locale.ROOT, "%02d", sections.size() + 1));
            section.setTitle(title);
            section.setParagraphs(paragraphs);
            sections.add(section);
        }
        return sections;
    }

    private static List<String> parseTitleLines(String json, String fallbackTitle) {
        if (StrUtil.isBlank(json)) {
            return List.of(fallbackTitle);
        }
        List<String> values = JsonUtils.parseArray(json, String.class);
        return values == null || values.isEmpty() ? List.of(fallbackTitle) : values;
    }

    private static List<WebsiteBlogSectionRespVO> parseSections(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        List<WebsiteBlogSectionRespVO> values = JsonUtils.parseArray(json, WebsiteBlogSectionRespVO.class);
        return values == null ? List.of() : values;
    }

    private static void validatePublishable(
            WebsiteBlogArticleDO article, List<WebsiteBlogSectionRespVO> sections) {
        if (StrUtil.hasBlank(article.getTitle(), article.getSlug(), article.getCategory(), article.getLabel(),
                article.getSummary(), article.getCoverImageUrl(), article.getCoverImageAlt())
                || sections.isEmpty()) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
    }

    private WebsiteBlogArticleDO verifyPreviewGrant(
            WebsiteBlogPreviewGrant grant, String requestOrigin) {
        if (grant == null || !Objects.equals(grant.getTenantId(), currentTenantId())) {
            throw exception(BLOG_PREVIEW_EXPIRED);
        }
        validateOrigin(grant.getPreviewOrigin(), requestOrigin);
        WebsiteBlogArticleDO article = websiteBlogArticleMapper.selectByIdForTenant(grant.getArticleId());
        if (article == null
                || !Objects.equals(article.getSiteId(), grant.getSiteId())
                || !Objects.equals(article.getLocale(), grant.getLocale())
                || !Objects.equals(article.getVersion(), grant.getVersion())) {
            throw exception(BLOG_PREVIEW_EXPIRED);
        }
        return article;
    }

    private WebsiteBlogArticleDO getRequiredArticle(Long id) {
        WebsiteBlogArticleDO article = websiteBlogArticleMapper.selectByIdForTenant(id);
        if (article == null) {
            throw exception(BLOG_ARTICLE_NOT_EXISTS);
        }
        return article;
    }

    private void classifyAtomicFailure(Long id) {
        getRequiredArticle(id);
        throw exception(BLOG_ARTICLE_VERSION_CONFLICT);
    }

    private static String normalizeLegacyPath(String value) {
        String path = trim(value);
        if (StrUtil.isBlank(path)) {
            return "";
        }
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")
                || path.contains("?") || path.contains("#")) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
        return path;
    }

    private static String normalizeImageUrl(String value, boolean required) {
        String candidate = trim(value);
        if (StrUtil.isBlank(candidate)) {
            if (required) {
                throw exception(BLOG_ARTICLE_INVALID);
            }
            return "";
        }
        if (candidate.startsWith("/") && !candidate.startsWith("//") && !candidate.contains("\\")) {
            return candidate;
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.isOpaque() || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null) {
                throw exception(BLOG_ARTICLE_INVALID);
            }
            return candidate;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw exception(BLOG_ARTICLE_INVALID);
        }
    }

    private static String readTime(List<WebsiteBlogSectionRespVO> sections, String summary) {
        int words = countWords(summary);
        for (WebsiteBlogSectionRespVO section : sections) {
            words += countWords(section.getTitle());
            for (String paragraph : section.getParagraphs()) {
                words += countWords(paragraph);
            }
        }
        int minutes = Math.max(1, (int) Math.ceil(words / (double) WORDS_PER_MINUTE));
        return minutes + " min read";
    }

    private static int countWords(String value) {
        if (StrUtil.isBlank(value)) {
            return 0;
        }
        int count = 0;
        var matcher = WORD_PATTERN.matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String slugify(String value) {
        return trim(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String randomToken(String prefix) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String originOf(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw exception(BLOG_PREVIEW_ORIGIN_MISMATCH);
        }
    }

    private static void validateOrigin(String expectedOrigin, String requestOrigin) {
        if (StrUtil.isBlank(requestOrigin)) {
            return;
        }
        if (!Objects.equals(expectedOrigin, originOf(requestOrigin))) {
            throw exception(BLOG_PREVIEW_ORIGIN_MISMATCH);
        }
    }

    private static Long currentTenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static String currentUpdater() {
        return Objects.toString(SecurityFrameworkUtils.getLoginUserId(), "");
    }

}
