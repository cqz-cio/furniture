package cn.iocoder.yudao.module.seo.service.code;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.code.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.code.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.code.*;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;

@Service
@Validated
public class WebsiteCodeServiceImpl implements WebsiteCodeService {
    @Resource private WebsiteCodeConfigMapper configMapper;
    @Resource private WebsiteCodeHistoryMapper historyMapper;
    @Resource private SeoSiteConfigMapper siteMapper;

    @Override
    public WebsiteCodeRespVO getDraft(Long siteId) {
        SeoSiteConfigDO site = requireSite(siteId, false);
        return response(site, configMapper.selectSite(siteId, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebsiteCodeRespVO saveDraft(WebsiteCodeSaveReqVO request) {
        SeoSiteConfigDO site = requireSite(request.getSiteId(), true);
        WebsiteCodeConfigDO config = lockedConfig(request);
        config.setDraftJson(JsonUtils.toJsonString(request.getContent()));
        write(config, "SAVE");
        return response(site, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebsiteCodeRespVO publish(WebsiteCodeVersionReqVO request) {
        SeoSiteConfigDO site = requireSite(request.getSiteId(), true);
        WebsiteCodeConfigDO config = lockedConfig(request);
        if (config.getId() == null) throw exception(WEBSITE_CODE_DRAFT_REQUIRED);
        // A row lock and the caller's version serialize saves, restores and publishes.
        config.setPublishedJson(config.getDraftJson());
        config.setPublishedVersion(config.getVersion() + 1);
        config.setPublishedTime(LocalDateTime.now());
        write(config, "PUBLISH");
        return response(site, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebsiteCodeRespVO restoreDraft(WebsiteCodeRestoreReqVO request) {
        SeoSiteConfigDO site = requireSite(request.getSiteId(), true);
        WebsiteCodeConfigDO config = lockedConfig(request);
        WebsiteCodeHistoryDO history = historyMapper.selectSiteRecord(request.getSiteId(), request.getHistoryId());
        if (history == null) throw exception(WEBSITE_CODE_HISTORY_NOT_EXISTS);
        config.setDraftJson(history.getSnapshotJson());
        write(config, "RESTORE");
        return response(site, config);
    }

    @Override
    public List<WebsiteCodeHistoryRespVO> getHistory(Long siteId) {
        requireSite(siteId, false);
        return historyMapper.selectHistory(siteId).stream().map(row -> {
            WebsiteCodeHistoryRespVO result = BeanUtils.toBean(row, WebsiteCodeHistoryRespVO.class);
            result.setContent(JsonUtils.parseObject(row.getSnapshotJson(), WebsiteCodeContent.class));
            return result;
        }).toList();
    }

    @Override
    public WebsiteCodeRespVO getPublished(Long siteId) {
        // Public responses contain no draft, history or editor identity.
        SeoSiteConfigDO site = requireSite(siteId, false);
        WebsiteCodeConfigDO config = configMapper.selectSite(siteId, false);
        WebsiteCodeContent content = config == null || config.getPublishedJson() == null
                ? new WebsiteCodeContent() : JsonUtils.parseObject(config.getPublishedJson(), WebsiteCodeContent.class);
        for (WebsiteCodeContent.Section section : List.of(content.getHeader(), content.getBody(), content.getFooter())) {
            if (!Boolean.TRUE.equals(section.getEnabled())) section.setCode("");
        }
        return new WebsiteCodeRespVO().setSiteId(site.getSiteId())
                .setVersion(config == null ? null : config.getPublishedVersion()).setContent(content);
    }

    private SeoSiteConfigDO requireSite(Long siteId, boolean lock) {
        TenantContextHolder.getRequiredTenantId();
        SeoSiteConfigDO site = lock ? siteMapper.selectBySiteIdForUpdate(siteId) : siteMapper.selectBySiteId(siteId);
        if (site == null || !Objects.equals(site.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(SITE_CONFIG_NOT_EXISTS);
        }
        return site;
    }

    private WebsiteCodeConfigDO lockedConfig(WebsiteCodeVersionReqVO request) {
        // The existing tenant/site row also serializes the first configuration insert.
        WebsiteCodeConfigDO config = configMapper.selectSite(request.getSiteId(), true);
        if (config == null) {
            config = new WebsiteCodeConfigDO().setSiteId(request.getSiteId()).setVersion(0)
                    .setDraftJson(JsonUtils.toJsonString(new WebsiteCodeContent()));
            config.setTenantId(TenantContextHolder.getRequiredTenantId());
        }
        if (!Objects.equals(config.getVersion(), request.getVersion())) throw exception(WEBSITE_CODE_VERSION_CONFLICT);
        return config;
    }

    private void write(WebsiteCodeConfigDO config, String action) {
        config.setVersion(config.getVersion() + 1);
        config.setUpdateTime(LocalDateTime.now());
        if (config.getId() == null) configMapper.insert(config);
        else configMapper.updateById(config);
        WebsiteCodeHistoryDO history = new WebsiteCodeHistoryDO().setSiteId(config.getSiteId())
                .setVersion(config.getVersion()).setAction(action).setSnapshotJson(config.getDraftJson());
        history.setTenantId(TenantContextHolder.getRequiredTenantId());
        historyMapper.insert(history);
    }

    private WebsiteCodeRespVO response(SeoSiteConfigDO site, WebsiteCodeConfigDO config) {
        return new WebsiteCodeRespVO().setSiteId(site.getSiteId()).setSiteName(site.getSiteName()).setSiteUrl(site.getSiteUrl())
                .setVersion(config == null ? 0 : config.getVersion())
                .setPublishedVersion(config == null ? null : config.getPublishedVersion())
                .setUpdateTime(config == null ? null : config.getUpdateTime())
                .setPublishedTime(config == null ? null : config.getPublishedTime())
                .setContent(config == null ? new WebsiteCodeContent() : JsonUtils.parseObject(config.getDraftJson(), WebsiteCodeContent.class));
    }
}
