package cn.iocoder.yudao.module.system.service.inquiry.mail;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.crm.api.inquiry.dto.CrmWebsiteInquiryRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.inquiry.WebsiteInquiryMailConfigDO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 询盘邮件模板校验和安全渲染。
 */
@Component
public class WebsiteInquiryMailTemplateRenderer {

    public static final String DEFAULT_SENDER_NAME = "VANZ Inquiry Desk";
    public static final String DEFAULT_SUBJECT =
            "[VANZ New Inquiry #{inquiryNo}] {companyName} — {inquirySubject}";
    public static final String DEFAULT_CONTENT = """
            <div style="max-width:680px;margin:0 auto;font-family:Arial,sans-serif;color:#1f2937;line-height:1.6">
              <h2 style="margin:0 0 20px">New B2B inquiry #{inquiryNo}</h2>
              <table style="width:100%;border-collapse:collapse">
                <tr><td style="padding:7px 0;color:#6b7280">Received</td><td>{submittedAt}</td></tr>
                <tr><td style="padding:7px 0;color:#6b7280">Name</td><td>{customerName}</td></tr>
                <tr><td style="padding:7px 0;color:#6b7280">Company</td><td>{companyName}</td></tr>
                <tr><td style="padding:7px 0;color:#6b7280">Email</td><td><a href="{replyUrl}">{customerEmail}</a></td></tr>
                <tr><td style="padding:7px 0;color:#6b7280">Phone / WhatsApp</td><td>{phone}</td></tr>
                <tr><td style="padding:7px 0;color:#6b7280">Subject</td><td>{inquirySubject}</td></tr>
              </table>
              <div style="margin:22px 0;padding:18px;background:#f7f5f2;border-left:3px solid #9a6846">
                {inquiryMessage}
              </div>
              <p style="margin:0 0 6px;color:#6b7280">Source: {sourcePage}</p>
              <p style="margin:0 0 24px;color:#6b7280">Campaign: {utmSource} / {utmMedium} / {utmCampaign}</p>
              <p>
                <a href="{replyUrl}" style="display:inline-block;margin-right:10px;padding:11px 18px;background:#111827;color:#fff;text-decoration:none">Reply to customer</a>
                <a href="{erpDetailUrl}" style="display:inline-block;padding:10px 18px;border:1px solid #9ca3af;color:#111827;text-decoration:none">View in ERP</a>
              </p>
            </div>
            """;

    private static final List<String> AVAILABLE_VARIABLES = List.of(
            "inquiryNo", "externalInquiryId", "customerName", "customerEmail",
            "companyName", "phone", "inquirySubject", "inquiryMessage",
            "sourcePage", "locale", "utmSource", "utmMedium", "utmCampaign",
            "submittedAt", "erpDetailUrl", "replyUrl");
    private static final Set<String> ALLOWED_VARIABLES = Set.copyOf(AVAILABLE_VARIABLES);
    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");
    private static final Pattern FORBIDDEN_TEMPLATE_PATTERN = Pattern.compile(
            "(?i)(<\\s*(script|iframe|object|embed|form|input)|javascript\\s*:|"
                    + "vbscript\\s*:|data\\s*:\\s*text/html|expression\\s*\\(|url\\s*\\()");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Safelist EMAIL_SAFELIST = Safelist.relaxed()
            .addTags("table", "tbody", "thead", "tfoot", "tr", "td", "th", "hr")
            .addAttributes(":all", "style", "class")
            .addAttributes("a", "target")
            .addAttributes("table", "width", "border", "cellpadding", "cellspacing")
            .addAttributes("td", "width", "align", "valign")
            .addProtocols("a", "href", "http", "https", "mailto");

    public void validateTemplates(String subjectTemplate, String contentTemplate) {
        validateTemplate(subjectTemplate);
        validateTemplate(contentTemplate);
        if (FORBIDDEN_TEMPLATE_PATTERN.matcher(contentTemplate).find()) {
            throw new IllegalArgumentException("template contains unsafe HTML");
        }
    }

    public List<String> getAvailableVariables() {
        return AVAILABLE_VARIABLES;
    }

    private void validateTemplate(String template) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            if (!ALLOWED_VARIABLES.contains(matcher.group(1))) {
                throw new IllegalArgumentException(matcher.group(1));
            }
        }
    }

    public RenderedMail render(WebsiteInquiryMailConfigDO config,
                               CrmWebsiteInquiryRespDTO inquiry) {
        Map<String, Object> rawParams = buildRawParams(config, inquiry);
        String title = sanitizeHeader(StrUtil.format(config.getSubjectTemplate(), rawParams));

        Map<String, Object> htmlParams = new LinkedHashMap<>();
        rawParams.forEach((key, value) -> {
            String text = String.valueOf(value);
            if ("replyUrl".equals(key) || "erpDetailUrl".equals(key)) {
                htmlParams.put(key, text);
            } else {
                htmlParams.put(key, escapeHtmlText(text));
            }
        });
        String renderedHtml = StrUtil.format(config.getContentTemplate(), htmlParams);
        String safeHtml = Jsoup.clean(renderedHtml, "", EMAIL_SAFELIST,
                new Document.OutputSettings().prettyPrint(false));
        return new RenderedMail(title, safeHtml);
    }

    private Map<String, Object> buildRawParams(WebsiteInquiryMailConfigDO config,
                                                CrmWebsiteInquiryRespDTO inquiry) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("inquiryNo", inquiry.getId());
        params.put("externalInquiryId", display(inquiry.getExternalInquiryId()));
        params.put("customerName", display(inquiry.getContactName()));
        params.put("customerEmail", display(inquiry.getEmail()));
        params.put("companyName", display(inquiry.getCompanyName()));
        params.put("phone", buildPhone(inquiry.getCountryCode(), inquiry.getPhone()));
        params.put("inquirySubject", display(inquiry.getSubject()));
        params.put("inquiryMessage", displayMultiline(inquiry.getMessage()));
        params.put("sourcePage", display(inquiry.getSourcePage()));
        params.put("locale", display(inquiry.getLocale()));
        params.put("utmSource", display(inquiry.getUtmSource()));
        params.put("utmMedium", display(inquiry.getUtmMedium()));
        params.put("utmCampaign", display(inquiry.getUtmCampaign()));
        params.put("submittedAt", formatDateTime(inquiry.getSubmittedAt()));
        params.put("erpDetailUrl", buildErpDetailUrl(config.getErpBaseUrl(), inquiry.getId()));
        params.put("replyUrl", buildReplyUrl(inquiry));
        return params;
    }

    private static String buildReplyUrl(CrmWebsiteInquiryRespDTO inquiry) {
        String subject = "Re: " + display(inquiry.getSubject());
        String body = "Hi " + display(inquiry.getContactName()) + ",\r\n\r\n"
                + "Thank you for your inquiry.\r\n\r\n";
        return "mailto:" + normalize(inquiry.getEmail())
                + "?subject=" + urlEncode(subject)
                + "&body=" + urlEncode(body);
    }

    private static String buildErpDetailUrl(String baseUrl, Long inquiryId) {
        String normalized = normalize(baseUrl).replaceAll("/+$", "");
        return normalized.isEmpty() ? "#" : normalized + "/crm/clue/detail/" + inquiryId;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String buildPhone(String countryCode, String phone) {
        String value = (normalize(countryCode) + " " + normalize(phone)).trim();
        return value.isEmpty() ? "-" : value;
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    private static String display(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "-" : normalized;
    }

    private static String displayMultiline(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]+", " ")
                .trim();
    }

    private static String sanitizeHeader(String value) {
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    /**
     * Numeric entities remain text tokens after the final Jsoup parse and are
     * not reversed by the legacy mail-template HTML unescape behavior.
     */
    private static String escapeHtmlText(String value) {
        return value
                .replace("&", "&#38;")
                .replace("<", "&#60;")
                .replace(">", "&#62;")
                .replace("\"", "&#34;")
                .replace("\n", "<br>");
    }

    public static CrmWebsiteInquiryRespDTO sampleInquiry(String recipientEmail) {
        CrmWebsiteInquiryRespDTO inquiry = new CrmWebsiteInquiryRespDTO();
        inquiry.setId(10001L);
        inquiry.setExternalInquiryId("sample-inquiry");
        inquiry.setContactName("Alex Morgan");
        inquiry.setEmail(recipientEmail);
        inquiry.setCountryCode("+44");
        inquiry.setPhone("7700 900123");
        inquiry.setCompanyName("Northstar Interiors");
        inquiry.setSubject("Hotel dining chair project");
        inquiry.setMessage("We need 120 dining chairs for a hotel project.\n"
                + "Please advise available finishes and lead time.");
        inquiry.setSourcePage("/products/dining-room");
        inquiry.setLocale(Locale.ENGLISH.toLanguageTag());
        inquiry.setUtmSource("google");
        inquiry.setUtmMedium("cpc");
        inquiry.setUtmCampaign("sample");
        inquiry.setSubmittedAt(LocalDateTime.now());
        return inquiry;
    }

    public record RenderedMail(String title, String htmlContent) {
    }

}
