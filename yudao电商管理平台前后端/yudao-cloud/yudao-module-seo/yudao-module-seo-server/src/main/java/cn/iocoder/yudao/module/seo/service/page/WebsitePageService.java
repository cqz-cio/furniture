package cn.iocoder.yudao.module.seo.service.page;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.page.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.page.*;
import cn.iocoder.yudao.module.seo.dal.redis.page.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;

@Service
@Validated
public class WebsitePageService {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private WebsitePageMapper pageMapper;
    @Resource private WebsitePageRevisionMapper revisionMapper;
    @Resource private SeoSiteConfigMapper siteMapper;
    @Resource private WebsitePagePreviewRedisDAO previewDAO;

    public JsonNode getSchema(@Valid WebsitePageKeyReqVO key) {
        site(key.getSiteId(), false);
        return resource("schema.json");
    }

    public WebsitePageRespVO getDraft(@Valid WebsitePageKeyReqVO key) {
        site(key.getSiteId(), false);
        return response(requiredPage(key, false), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebsitePageRespVO initialize(@Valid WebsitePageKeyReqVO key) {
        // A site lock serializes first inserts; existing content is never overwritten.
        site(key.getSiteId(), true);
        WebsitePageDO row = pageMapper.selectPage(key.getSiteId(), key.getPageKey(), key.getLocale(), true);
        if (row == null) {
            JsonNode content = resource("home." + key.getLocale() + ".json");
            WebsitePageContentValidator.validate(content);
            row = new WebsitePageDO().setSiteId(key.getSiteId()).setPageKey(key.getPageKey())
                    .setLocale(key.getLocale()).setSchemaVersion(1).setDraftVersion(1)
                    .setDraftJson(content.toString());
            row.setTenantId(TenantContextHolder.getRequiredTenantId());
            pageMapper.insert(row);
        }
        return response(row, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebsitePageRespVO saveDraft(@Valid WebsitePageSaveReqVO request) {
        site(request.getSiteId(), true);
        WebsitePageDO row = versioned(request);
        WebsitePageContentValidator.validate(request.getContent());
        row.setDraftJson(request.getContent().toString()).setDraftVersion(row.getDraftVersion() + 1);
        pageMapper.updateById(row);
        return response(row, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebsitePageRespVO publish(@Valid WebsitePageVersionReqVO request) {
        site(request.getSiteId(), true);
        WebsitePageDO row = versioned(request);
        WebsitePageContentValidator.validate(JsonUtils.parseObject(row.getDraftJson(), JsonNode.class));
        if (row.getPublishedRevisionId() != null) {
            WebsitePageRevisionDO current = revisionMapper.selectRevision(row.getId(), row.getPublishedRevisionId());
            if (current != null && current.getRevision().equals(row.getDraftVersion())) return response(row, false);
        }
        WebsitePageRevisionDO revision = new WebsitePageRevisionDO().setPageId(row.getId())
                .setRevision(row.getDraftVersion()).setSchemaVersion(row.getSchemaVersion()).setContentJson(row.getDraftJson());
        revision.setTenantId(TenantContextHolder.getRequiredTenantId());
        revisionMapper.insert(revision);
        row.setPublishedRevisionId(revision.getId());
        pageMapper.updateById(row);
        return response(row, false);
    }

    public WebsitePageRespVO getPublished(@Valid WebsitePageKeyReqVO key) {
        site(key.getSiteId(), false);
        return response(requiredPage(key, false), true);
    }

    public List<WebsitePageRespVO> getHistory(@Valid WebsitePageKeyReqVO key) {
        site(key.getSiteId(), false);
        WebsitePageDO row = requiredPage(key, false);
        return revisionMapper.selectHistory(row.getId()).stream().map(revision ->
                new WebsitePageRespVO().setSiteId(row.getSiteId()).setPageKey(row.getPageKey())
                        .setLocale(row.getLocale()).setVersion(revision.getRevision())
                        .setContent(JsonUtils.parseObject(revision.getContentJson(), JsonNode.class))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createPreviewTicket(@Valid WebsitePageVersionReqVO request) {
        SeoSiteConfigDO config = site(request.getSiteId(), true);
        WebsitePageDO row = versioned(request);
        String base = config.getPreviewBaseUrl();
        if (base == null || base.isBlank()) throw exception(PAGE_PREVIEW_URL_REQUIRED);
        String origin = origin(base);
        WebsitePageRespVO snapshot = response(row, false);
        WebsitePageContentValidator.validate(snapshot.getContent());
        String token = token("ppv_");
        previewDAO.setTicket(token, new WebsitePagePreviewGrant(
                TenantContextHolder.getRequiredTenantId(), row.getSiteId(), origin, snapshot));
        return Map.of("previewUrl", base.replaceAll("/+$", "") + "/#/cms-preview?ticket=" + token,
                "expiresIn", 120);
    }

    public Map<String, Object> exchangePreviewTicket(String ticket, String requestOrigin) {
        if (ticket == null || !ticket.matches("ppv_[A-Za-z0-9_-]{43}")) throw exception(PAGE_PREVIEW_INVALID);
        WebsitePagePreviewGrant grant = previewDAO.consumeTicket(ticket);
        verify(grant, requestOrigin);
        String session = token("pps_");
        previewDAO.setSession(session, grant);
        return Map.of("previewSession", session, "expiresIn", 900,
                "pageKey", grant.page().getPageKey(), "locale", grant.page().getLocale());
    }

    public WebsitePageRespVO getPreview(String session, String requestOrigin) {
        if (session == null || !session.matches("pps_[A-Za-z0-9_-]{43}")) throw exception(PAGE_PREVIEW_INVALID);
        WebsitePagePreviewGrant grant = previewDAO.getSession(session);
        verify(grant, requestOrigin);
        return grant.page();
    }

    private void verify(WebsitePagePreviewGrant grant, String requestOrigin) {
        if (grant == null || !Objects.equals(grant.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(PAGE_PREVIEW_INVALID);
        SeoSiteConfigDO config = site(grant.siteId(), false);
        if (!Objects.equals(grant.origin(), origin(requestOrigin))
                || !Objects.equals(grant.origin(), origin(config.getPreviewBaseUrl())))
            throw exception(PAGE_PREVIEW_INVALID);
    }

    private SeoSiteConfigDO site(Long siteId, boolean lock) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        SeoSiteConfigDO config = lock ? siteMapper.selectBySiteIdForUpdate(siteId) : siteMapper.selectBySiteId(siteId);
        if (config == null || !Objects.equals(config.getTenantId(), tenant)
                || !"TRIPEER_CORPORATE".equals(config.getNavigationTemplate())) throw exception(PAGE_SITE_UNAVAILABLE);
        return config;
    }

    private WebsitePageDO requiredPage(WebsitePageKeyReqVO key, boolean lock) {
        WebsitePageDO row = pageMapper.selectPage(key.getSiteId(), key.getPageKey(), key.getLocale(), lock);
        if (row == null) throw exception(PAGE_NOT_INITIALIZED);
        return row;
    }

    private WebsitePageDO versioned(WebsitePageVersionReqVO request) {
        WebsitePageDO row = requiredPage(request, true);
        if (!Objects.equals(row.getDraftVersion(), request.getExpectedVersion())) throw exception(PAGE_VERSION_CONFLICT);
        return row;
    }

    private WebsitePageRespVO response(WebsitePageDO row, boolean published) {
        WebsitePageRevisionDO revision = row.getPublishedRevisionId() == null ? null
                : revisionMapper.selectRevision(row.getId(), row.getPublishedRevisionId());
        if (published && revision == null) throw exception(PAGE_NOT_PUBLISHED);
        return new WebsitePageRespVO().setSiteId(row.getSiteId()).setPageKey(row.getPageKey()).setLocale(row.getLocale())
                .setVersion(published ? revision.getRevision() : row.getDraftVersion())
                .setPublishedVersion(revision == null ? null : revision.getRevision())
                .setContent(JsonUtils.parseObject(published ? revision.getContentJson() : row.getDraftJson(), JsonNode.class));
    }

    private JsonNode resource(String name) {
        try (var input = new ClassPathResource("cms/tripeer/" + name).getInputStream()) {
            return JsonUtils.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonNode.class);
        } catch (java.io.IOException ex) { throw new IllegalStateException("Missing bundled CMS resource", ex); }
    }

    private static String token(String prefix) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String origin(String value) {
        try {
            URI uri = URI.create(Objects.requireNonNull(value));
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !Set.of("http", "https").contains(uri.getScheme())) throw exception(PAGE_PREVIEW_INVALID);
            int port = uri.getPort();
            if (("http".equals(uri.getScheme()) && port == 80) || ("https".equals(uri.getScheme()) && port == 443)) port = -1;
            return uri.getScheme() + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
        } catch (IllegalArgumentException | NullPointerException ex) { throw exception(PAGE_PREVIEW_INVALID); }
    }
}
