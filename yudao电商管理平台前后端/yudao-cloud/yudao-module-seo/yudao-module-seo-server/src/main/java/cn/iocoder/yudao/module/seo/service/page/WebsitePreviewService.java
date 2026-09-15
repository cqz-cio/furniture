package cn.iocoder.yudao.module.seo.service.page;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.redis.page.*;
import cn.iocoder.yudao.module.seo.service.navigation.WebsiteNavigationService;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import cn.iocoder.yudao.module.seo.service.media.WebsiteMediaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;

@Service
public class WebsitePreviewService {
    @Resource private WebsitePageService pages;
    @Resource private WebsiteNavigationService navigation;
    @Resource private WebsiteBlogService blogs;
    @Resource private WebsiteMediaService media;
    @Resource private SeoSiteConfigMapper sites;
    @Resource private WebsitePreviewRedisDAO tokens;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(WebsitePreviewReqVO request) {
        var site = sites.selectBySiteIdForUpdate(request.getSiteId());
        if (site == null || !Objects.equals(site.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !"TRIPEER_CORPORATE".equals(site.getNavigationTemplate())) throw exception(PAGE_SITE_UNAVAILABLE);
        if (site.getPreviewBaseUrl() == null || site.getPreviewBaseUrl().isBlank()) throw exception(PAGE_PREVIEW_URL_REQUIRED);
        String origin = WebsitePageService.origin(site.getPreviewBaseUrl());
        var page = pages.getDraft(request);
        var nav = navigation.getDraftPreview(request.getSiteId(), request.getLocale());
        if ((request.getPageVersion() != null && !Objects.equals(request.getPageVersion(), page.getVersion()))
                || (request.getNavigationVersion() != null && !Objects.equals(request.getNavigationVersion(), nav.getVersion())))
            throw exception(PAGE_VERSION_CONFLICT);
        WebsitePageContentValidator.validate(page.getContent());
        media.validateImage(page.getContent().path("modules").path("hero").path("image"));
        AppWebsiteBlogArticleRespVO article = null;
        if (request.getArticleId() != null) {
            var draft = blogs.getArticle(request.getArticleId());
            if (!Objects.equals(draft.getSiteId(), request.getSiteId()) || !Objects.equals(draft.getLocale(), request.getLocale()))
                throw exception(BLOG_ARTICLE_IDENTITY_IMMUTABLE);
            article = blogs.getDraftPreview(request.getArticleId(), request.getArticleVersion());
        }
        String ticket = token("spv_");
        tokens.setTicket(ticket, new WebsitePreviewGrant(TenantContextHolder.getRequiredTenantId(), request.getSiteId(),
                origin, request.getLocale(), page, nav, article));
        return Map.of("previewUrl", site.getPreviewBaseUrl().replaceAll("/+$", "") + "/#/cms-preview?ticket=" + ticket, "expiresIn", 120);
    }
    public Map<String, Object> exchange(String ticket, String origin) {
        if (ticket == null || !ticket.matches("spv_[A-Za-z0-9_-]{43}")) throw exception(PAGE_PREVIEW_INVALID);
        var grant = tokens.consumeTicket(ticket);
        verify(grant, origin);
        String session = token("sps_");
        tokens.setSession(session, grant);
        return Map.of("previewSession", session, "expiresIn", 900, "locale", grant.locale());
    }
    public Map<String, Object> get(String session, String origin) {
        if (session == null || !session.matches("sps_[A-Za-z0-9_-]{43}")) throw exception(PAGE_PREVIEW_INVALID);
        var grant = tokens.getSession(session);
        verify(grant, origin);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locale", grant.locale()); result.put("page", grant.page()); result.put("navigation", grant.navigation());
        result.put("article", grant.article());
        return result;
    }
    private void verify(WebsitePreviewGrant grant, String origin) {
        if (grant == null || !Objects.equals(grant.tenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(PAGE_PREVIEW_INVALID);
        var site = sites.selectBySiteId(grant.siteId());
        if (site == null || !Objects.equals(site.getTenantId(), grant.tenantId())
                || !"TRIPEER_CORPORATE".equals(site.getNavigationTemplate())
                || !Objects.equals(grant.origin(), WebsitePageService.origin(origin))
                || !Objects.equals(grant.origin(), WebsitePageService.origin(site.getPreviewBaseUrl()))) throw exception(PAGE_PREVIEW_INVALID);
    }
    private static String token(String prefix) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
