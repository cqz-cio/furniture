package cn.iocoder.yudao.module.system.service.inquiry.mail;

import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailConfigDO;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteInquiryMailTemplateRendererTest {

    private final WebsiteInquiryMailTemplateRenderer renderer =
            new WebsiteInquiryMailTemplateRenderer();

    @Test
    void testRenderEscapesCustomerInputAndBuildsReplyLinks() {
        WebsiteInquiryMailConfigDO config = defaultConfig();
        CrmWebsiteInquiryRespDTO inquiry = WebsiteInquiryMailTemplateRenderer.sampleInquiry(
                "alex@example.com");
        inquiry.setContactName("<img src=x onerror=alert(1)>");
        inquiry.setSubject("Chair quote\r\nBcc: attacker@example.com");
        inquiry.setMessage("<script>alert(1)</script>\nNeed 120 chairs");
        inquiry.setSubmittedAt(LocalDateTime.of(2026, 7, 30, 12, 30));

        WebsiteInquiryMailTemplateRenderer.RenderedMail result =
                renderer.render(config, inquiry);

        assertFalse(result.title().contains("\r"));
        assertFalse(result.title().contains("\n"));
        assertTrue(Jsoup.parse(result.htmlContent()).select("script, [onerror]").isEmpty());
        assertTrue(result.htmlContent().contains("alex@example.com"));
        assertTrue(result.htmlContent().contains("mailto:alex@example.com"));
        assertTrue(result.htmlContent().contains(
                "https://erp.example.com/crm/clue/detail/10001"));
    }

    @Test
    void testValidateTemplatesRejectsUnknownVariablesAndUnsafeHtml() {
        assertThrows(IllegalArgumentException.class,
                () -> renderer.validateTemplates(
                        "New {unknownVariable}",
                        WebsiteInquiryMailTemplateRenderer.DEFAULT_CONTENT));
        assertThrows(IllegalArgumentException.class,
                () -> renderer.validateTemplates(
                        WebsiteInquiryMailTemplateRenderer.DEFAULT_SUBJECT,
                        "<script>alert(1)</script>"));
    }

    private static WebsiteInquiryMailConfigDO defaultConfig() {
        WebsiteInquiryMailConfigDO config = new WebsiteInquiryMailConfigDO();
        config.setSubjectTemplate(WebsiteInquiryMailTemplateRenderer.DEFAULT_SUBJECT);
        config.setContentTemplate(WebsiteInquiryMailTemplateRenderer.DEFAULT_CONTENT);
        config.setErpBaseUrl("https://erp.example.com");
        return config;
    }

}
