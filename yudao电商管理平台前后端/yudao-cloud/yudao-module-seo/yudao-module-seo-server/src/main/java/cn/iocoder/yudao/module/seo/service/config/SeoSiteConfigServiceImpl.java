package cn.iocoder.yudao.module.seo.service.config;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.seo.controller.admin.config.vo.SeoSiteConfigSaveReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.SITE_CONFIG_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.SITE_CONFIG_URL_INVALID;

@Service
@Validated
public class SeoSiteConfigServiceImpl implements SeoSiteConfigService {

    private static final String DEFAULT_ROBOTS = "index,follow";
    private static final String DEFAULT_LOCALE = "zh-CN";

    @Resource
    private SeoSiteConfigMapper siteConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSiteConfig(SeoSiteConfigSaveReqVO reqVO) {
        SeoSiteConfigDO config = BeanUtils.toBean(reqVO, SeoSiteConfigDO.class)
                .setSiteUrl(normalizeSiteUrl(reqVO.getSiteUrl()))
                .setDefaultTitleSuffix(defaultIfBlank(reqVO.getDefaultTitleSuffix(), ""))
                .setDefaultDescription(defaultIfBlank(reqVO.getDefaultDescription(), ""))
                .setDefaultRobots(defaultIfBlank(reqVO.getDefaultRobots(), DEFAULT_ROBOTS))
                .setDefaultOgImage(defaultIfBlank(reqVO.getDefaultOgImage(), ""))
                .setDefaultLocale(defaultIfBlank(reqVO.getDefaultLocale(), DEFAULT_LOCALE));
        SeoSiteConfigDO existing = siteConfigMapper.selectBySiteId(reqVO.getSiteId());
        if (existing != null) {
            updateExisting(config, existing);
            return;
        }
        try {
            siteConfigMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            // Another request may have inserted the tenant/site row between select and insert.
            // A locking read is a current read in MySQL, so it sees the concurrent winner even under REPEATABLE READ.
            existing = siteConfigMapper.selectBySiteIdForUpdate(reqVO.getSiteId());
            if (existing == null) {
                throw ex;
            }
            updateExisting(config, existing);
        }
    }

    @Override
    public SeoSiteConfigDO getSiteConfig(Long siteId) {
        return siteConfigMapper.selectBySiteId(siteId);
    }

    @Override
    public SeoSiteConfigDO getRequiredSiteConfig(Long siteId) {
        SeoSiteConfigDO config = getSiteConfig(siteId);
        if (config == null) {
            throw exception(SITE_CONFIG_NOT_EXISTS);
        }
        return config;
    }

    private void updateExisting(SeoSiteConfigDO config, SeoSiteConfigDO existing) {
        config.setId(existing.getId());
        siteConfigMapper.updateById(config);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    static String normalizeSiteUrl(String value) {
        if (value == null) {
            throw exception(SITE_CONFIG_URL_INVALID);
        }
        String candidate = value.trim();
        if (candidate.contains("\\")) {
            throw exception(SITE_CONFIG_URL_INVALID);
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.isOpaque() || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw exception(SITE_CONFIG_URL_INVALID);
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = uri.normalize().getRawPath();
            while (path != null && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String authorityHost = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
                    ? "[" + host + "]" : host;
            String authority = port == -1 ? authorityHost : authorityHost + ":" + port;
            return new URI(scheme + "://" + authority + (path == null ? "" : path)).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw exception(SITE_CONFIG_URL_INVALID);
        }
    }

}
