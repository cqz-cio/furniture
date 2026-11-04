package cn.iocoder.yudao.module.system.framework.inquiry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 官网询盘“独立站点隔离”配置。
 *
 * <p>与旧 {@link WebsiteInquiryProperties}（锁 VANZ 租户 162）完全分离，用于让
 * Trendz Green 等新品牌通过独立站点配置接入，而不触碰旧入口的租户/密钥校验。</p>
 *
 * <p>默认（无任何站点条目）表示全部禁用：不向任何租户、任何站点开放询盘写入，
 * 也不产生站内信或邮件。站点密钥只允许配置在服务端环境变量中，禁止下发到浏览器。</p>
 *
 * <p>站点通知与邮件默认关闭，且任何站点都不得复用 VANZ(162)/Oakved(121) 保留租户
 * 或 VANZ 站内信模板。</p>
 */
@ConfigurationProperties(prefix = "yudao.website-inquiry")
@Data
public class WebsiteInquirySiteProperties {

    /** VANZ 保留租户。 */
    public static final long VANZ_TENANT_ID = 162L;
    /** Oakved 保留租户。 */
    public static final long OAKVED_TENANT_ID = 121L;
    /** 独立站点禁止占用的保留租户集合。 */
    public static final Set<Long> RESERVED_TENANT_IDS = Set.of(VANZ_TENANT_ID, OAKVED_TENANT_ID);
    /** 站点共享密钥最小长度。 */
    public static final int MIN_SHARED_SECRET_LENGTH = 24;

    /**
     * 站点键 → 站点配置。默认空，即没有任何站点可用（全禁用）。
     */
    private Map<String, Site> sites = new LinkedHashMap<>();

    /**
     * 按站点键解析站点配置；未知站点返回 {@code null}。
     */
    public Site getSite(String siteKey) {
        if (siteKey == null) {
            return null;
        }
        return sites.get(siteKey);
    }

    /**
     * 独立站点配置。通知与邮件默认关闭，模板无默认值（不得回落到 VANZ 模板）。
     */
    @Data
    public static class Site {

        /** 是否启用该站点询盘写入。 */
        private boolean enabled;
        /** 该站点解析到的租户编号（服务端解析，浏览器不可选）。 */
        private Long tenantId;
        /** 站点 Worker 与 ERP 之间的共享密钥（服务端专属）。 */
        private String sharedSecret;
        /** 是否启用入库后的站内信通知。默认关闭。 */
        private boolean notifyEnabled;
        /** 是否启用入库后的邮件转发。默认关闭。 */
        private boolean mailEnabled;
        /** 站内信模板编码（仅在 notifyEnabled 时使用，无默认值）。 */
        private String templateCode;

    }

    /**
     * 在 Spring 启动阶段校验站点隔离配置，避免把站点路由到保留租户或复用密钥。
     *
     * <p>等价于 {@link #validateForStartup(String, String)}，但不对旧 VANZ 密钥/模板做
     * 跨配置检查（供不依赖旧配置的独立单测使用）。</p>
     */
    public void validateForStartup() {
        validateForStartup(null, null);
    }

    /**
     * 校验站点隔离配置，并把旧 VANZ 的密钥与站内信模板作为“不得复用”的保留值一并检查。
     *
     * @param vanzSharedSecret 旧 {@link WebsiteInquiryProperties#getSharedSecret()} 值，可为空
     * @param vanzTemplateCode 旧 {@link WebsiteInquiryProperties#getTemplateCode()} 值，可为空
     */
    public void validateForStartup(String vanzSharedSecret, String vanzTemplateCode) {
        Set<String> seenSecrets = new HashSet<>();
        Set<Long> seenTenants = new HashSet<>();
        for (Map.Entry<String, Site> entry : sites.entrySet()) {
            Site site = entry.getValue();
            if (site == null) {
                continue;
            }
            Long tenantId = site.getTenantId();
            // 保留租户无论启用与否都拒绝，与旧 VANZ 校验策略一致。
            if (tenantId != null && RESERVED_TENANT_IDS.contains(tenantId)) {
                throw new IllegalStateException("website-inquiry site '" + entry.getKey()
                        + "' must not use reserved tenant " + tenantId
                        + " (162=VANZ, 121=Oakved)");
            }
            if (!site.isEnabled()) {
                continue;
            }
            if (tenantId == null || tenantId <= 0) {
                throw new IllegalStateException("website-inquiry site '" + entry.getKey()
                        + "' tenant-id must be a positive, non-reserved id when enabled");
            }
            if (site.getSharedSecret() == null
                    || site.getSharedSecret().trim().length() < MIN_SHARED_SECRET_LENGTH) {
                throw new IllegalStateException("website-inquiry site '" + entry.getKey()
                        + "' shared-secret must contain at least " + MIN_SHARED_SECRET_LENGTH
                        + " characters");
            }
            // 两个启用站点不得复用同一密钥，避免一个站点的 Worker 冒充另一个站点。
            if (!seenSecrets.add(site.getSharedSecret())) {
                throw new IllegalStateException("website-inquiry sites must not share the same"
                        + " shared-secret (duplicate on site '" + entry.getKey() + "')");
            }
            // 站点密钥不得复用旧 VANZ 密钥，避免旧 VANZ Worker 与新站点交叉鉴权。
            if (isNotBlank(vanzSharedSecret) && vanzSharedSecret.equals(site.getSharedSecret())) {
                throw new IllegalStateException("website-inquiry site '" + entry.getKey()
                        + "' must not reuse the legacy VANZ shared-secret");
            }
            // 站点站内信模板不得复用旧 VANZ 模板。
            if (isNotBlank(vanzTemplateCode)
                    && vanzTemplateCode.equals(site.getTemplateCode())) {
                throw new IllegalStateException("website-inquiry site '" + entry.getKey()
                        + "' must not reuse the legacy VANZ notify template '"
                        + vanzTemplateCode + "'");
            }
            // 两个启用站点不得映射同一租户，否则同一 externalInquiryId 会在租户内跨站点串单。
            if (!seenTenants.add(tenantId)) {
                throw new IllegalStateException("website-inquiry sites must map to distinct"
                        + " tenants (duplicate tenant " + tenantId + " on site '"
                        + entry.getKey() + "')");
            }
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
