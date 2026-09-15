package cn.iocoder.yudao.module.seo.service.page;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.PAGE_CONTENT_INVALID;

/** Fixed v1 contract: only fields with a real homepage binding are accepted. */
public final class WebsitePageContentValidator {
    private WebsitePageContentValidator() {}
    public static void validate(JsonNode content) {
        object(content, Set.of("schemaVersion", "modules"));
        if (!content.path("schemaVersion").isIntegralNumber() || !content.path("schemaVersion").canConvertToInt()
                || content.path("schemaVersion").asInt() != 1) fail();
        var modules = content.path("modules");
        object(modules, Set.of("hero"));
        var hero = modules.path("hero");
        object(hero, Set.of("title", "subtitle", "body", "image"));
        text(hero.path("title"), 160, true);
        text(hero.path("subtitle"), 240, false);
        text(hero.path("body"), 2000, false);
        var image = hero.path("image");
        object(image, Set.of("url", "alt"));
        text(image.path("url"), 1024, true);
        text(image.path("alt"), 240, true);
        String url = image.path("url").asText();
        try {
            URI uri = new URI(url);
            // Relative files must be bundled website assets; external images use HTTPS.
            boolean asset = url.startsWith("/assets/") && !url.contains("..")
                    && !url.contains("%") && !url.contains("\\") && uri.getRawQuery() == null && uri.getRawFragment() == null;
            // HTTP is limited to the managed file route for HTTP test sites; the service
            // must also resolve this exact URL to a current-tenant media record.
            boolean managed = uri.getPath() != null && uri.getPath().matches(
                    "/admin-api/infra/file/[0-9]+/get/website-media/[0-9]+/[A-Za-z0-9/.-]+");
            boolean external = ("https".equals(uri.getScheme()) || (managed && "http".equals(uri.getScheme()))) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null && uri.getRawQuery() == null;
            if (!asset && !external) fail();
        } catch (java.net.URISyntaxException ex) { fail(); }
    }
    private static void object(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size()) fail();
        node.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) fail(); });
        for (String field : fields) if (!node.hasNonNull(field)) fail();
    }
    private static void text(JsonNode value, int max, boolean required) {
        if (!value.isTextual() || value.asText().length() > max
                || (required && value.asText().isBlank())
                || value.asText().chars().anyMatch(c -> c < 32 && c != 10 && c != 13 && c != 9)) fail();
    }
    private static void fail() { throw exception(PAGE_CONTENT_INVALID); }
}
