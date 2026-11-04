package cn.iocoder.yudao.module.system.framework.inquiry.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebsiteInquirySitePropertiesTest {

    @Test
    void validateForStartup_allowsEmptySites() {
        assertDoesNotThrow(() -> new WebsiteInquirySiteProperties().validateForStartup());
    }

    @Test
    void validateForStartup_allowsDisabledSiteWithoutSecretOrTenant() {
        WebsiteInquirySiteProperties properties = with("trendzgreen", new WebsiteInquirySiteProperties.Site());

        assertDoesNotThrow(() -> properties.validateForStartup());
    }

    @Test
    void validateForStartup_rejectsReservedVanzTenantEvenWhileDisabled() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setTenantId(162L);

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site).validateForStartup());
    }

    @Test
    void validateForStartup_rejectsReservedOakvedTenantEvenWhileDisabled() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setTenantId(121L);

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site).validateForStartup());
    }

    @Test
    void validateForStartup_rejectsEnabledSiteWithoutTenant() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setSharedSecret("a-random-server-only-secret-32-chars");

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site).validateForStartup());
    }

    @Test
    void validateForStartup_rejectsEnabledSiteWithShortSecret() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setTenantId(900001L);
        site.setSharedSecret("too-short");

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site).validateForStartup());
    }

    @Test
    void validateForStartup_acceptsValidEnabledSite() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setTenantId(900001L);
        site.setSharedSecret("a-random-server-only-secret-32-chars");

        assertDoesNotThrow(() -> with("trendzgreen", site).validateForStartup());
    }

    @Test
    void validateForStartup_rejectsSharedSecretAcrossSites() {
        WebsiteInquirySiteProperties.Site a = new WebsiteInquirySiteProperties.Site();
        a.setEnabled(true);
        a.setTenantId(900001L);
        a.setSharedSecret("a-random-server-only-secret-32-chars");
        WebsiteInquirySiteProperties.Site b = new WebsiteInquirySiteProperties.Site();
        b.setEnabled(true);
        b.setTenantId(900002L);
        b.setSharedSecret("a-random-server-only-secret-32-chars");

        Map<String, WebsiteInquirySiteProperties.Site> sites = new LinkedHashMap<>();
        sites.put("site-a", a);
        sites.put("site-b", b);
        WebsiteInquirySiteProperties properties = new WebsiteInquirySiteProperties();
        properties.setSites(sites);

        assertThrows(IllegalStateException.class, properties::validateForStartup);
    }

    @Test
    void validateForStartup_rejectsReuseOfLegacyVanzSecret() {
        WebsiteInquirySiteProperties.Site site = enabledSite(900001L,
                "vanz-legacy-server-only-secret-32-chars");

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site)
                        .validateForStartup("vanz-legacy-server-only-secret-32-chars", null));
    }

    @Test
    void validateForStartup_rejectsReuseOfLegacyVanzTemplate() {
        WebsiteInquirySiteProperties.Site site = enabledSite(900001L,
                "a-random-server-only-secret-32-chars");
        site.setNotifyEnabled(true);
        site.setTemplateCode("vanz_website_inquiry");

        assertThrows(IllegalStateException.class,
                () -> with("trendzgreen", site)
                        .validateForStartup(null, "vanz_website_inquiry"));
    }

    @Test
    void validateForStartup_rejectsDuplicateTenantAcrossSites() {
        Map<String, WebsiteInquirySiteProperties.Site> sites = new LinkedHashMap<>();
        sites.put("site-a", enabledSite(900001L, "secret-a-server-only-32-chars-aaaa"));
        sites.put("site-b", enabledSite(900001L, "secret-b-server-only-32-chars-bbbb"));
        WebsiteInquirySiteProperties properties = new WebsiteInquirySiteProperties();
        properties.setSites(sites);

        assertThrows(IllegalStateException.class, properties::validateForStartup);
    }

    @Test
    void getSite_resolvesKnownSiteAndRejectsUnknown() {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setTenantId(900001L);
        WebsiteInquirySiteProperties properties = with("trendzgreen", site);

        assertSame(site, properties.getSite("trendzgreen"));
        assertNull(properties.getSite("unknown"));
        assertNull(properties.getSite(null));
    }

    private static WebsiteInquirySiteProperties.Site enabledSite(long tenantId, String secret) {
        WebsiteInquirySiteProperties.Site site = new WebsiteInquirySiteProperties.Site();
        site.setEnabled(true);
        site.setTenantId(tenantId);
        site.setSharedSecret(secret);
        return site;
    }

    private static WebsiteInquirySiteProperties with(String key, WebsiteInquirySiteProperties.Site site) {
        Map<String, WebsiteInquirySiteProperties.Site> sites = new LinkedHashMap<>();
        sites.put(key, site);
        WebsiteInquirySiteProperties properties = new WebsiteInquirySiteProperties();
        properties.setSites(sites);
        return properties;
    }

}
