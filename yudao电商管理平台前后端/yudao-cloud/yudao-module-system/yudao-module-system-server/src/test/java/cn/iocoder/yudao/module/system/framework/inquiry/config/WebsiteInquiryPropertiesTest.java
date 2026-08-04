package cn.iocoder.yudao.module.system.framework.inquiry.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebsiteInquiryPropertiesTest {

    @Test
    void validateForStartup_allowsDisabledUnconfiguredAdapter() {
        WebsiteInquiryProperties properties = new WebsiteInquiryProperties();

        assertDoesNotThrow(properties::validateForStartup);
    }

    @Test
    void validateForStartup_acceptsVanzTenantAndStrongSecret() {
        WebsiteInquiryProperties properties = new WebsiteInquiryProperties();
        properties.setEnabled(true);
        properties.setTenantId(162L);
        properties.setSharedSecret("a-random-server-only-secret-32-chars");

        assertDoesNotThrow(properties::validateForStartup);
    }

    @Test
    void validateForStartup_rejectsOakvedTenantEvenWhileDisabled() {
        WebsiteInquiryProperties properties = new WebsiteInquiryProperties();
        properties.setTenantId(121L);

        assertThrows(IllegalStateException.class, properties::validateForStartup);
    }

    @Test
    void validateForStartup_rejectsShortSecretWhenEnabled() {
        WebsiteInquiryProperties properties = new WebsiteInquiryProperties();
        properties.setEnabled(true);
        properties.setTenantId(162L);
        properties.setSharedSecret("too-short");

        assertThrows(IllegalStateException.class, properties::validateForStartup);
    }

}
