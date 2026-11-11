package cn.iocoder.yudao.module.seo.service.page;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.PAGE_CONTENT_INVALID;

/** Catalog v1 is a separate binding; it never relaxes the TRIPEER homepage contract. */
public final class TablewareCatalogContentValidator {
    private TablewareCatalogContentValidator() {}
    public static void validate(JsonNode content) {
        fields(content, Set.of("schemaVersion", "products"));
        if (!content.path("schemaVersion").isIntegralNumber() || !content.path("schemaVersion").canConvertToInt() || content.path("schemaVersion").asInt()!=1) fail();
        JsonNode products=content.path("products");
        if (!products.isArray() || products.size()>500) fail();
        Set<String> slugs=new HashSet<>();
        for (JsonNode p:products) {
            fields(p,Set.of("slug","name","category","subcategory","material","type","image","description","sourceUrl","sourceDate","specs","notes","sourceBlocks","links","migrationNotes","sourceId","sourcePath","sourceModifiedAt","sourceContentSha256"));
            String slug=text(p.path("slug"),140,true);
            if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !slugs.add(slug)) fail();
            text(p.path("name"),200,true); text(p.path("description"),8000,true);
            if (!Set.of("cups-straws","food-containers","tableware","bags").contains(text(p.path("category"),60,true))) fail();
            if (!text(p.path("subcategory"),80,true).matches("[a-z0-9]+(?:-[a-z0-9]+)*")) fail();
            text(p.path("material"),80,true);
            if (!Set.of("cup","lid","container","cutlery","bag","straw","plate","bowl").contains(text(p.path("type"),30,true))) fail();
            asset(text(p.path("image"),500,true));
            String source=text(p.path("sourceUrl"),1000,true);
            try { URI u=URI.create(source); if (!"https".equals(u.getScheme()) || !"www.trendzgreen.com".equals(u.getHost()) || u.getUserInfo()!=null) fail(); } catch (IllegalArgumentException e) { fail(); }
            if (!text(p.path("sourceDate"),10,true).matches("\\d{4}-\\d{2}-\\d{2}")) fail();
            JsonNode specs=p.path("specs");
            if (!specs.isArray() || specs.size()>200) fail();
            Set<String> codes=new HashSet<>();
            for(JsonNode s:specs) {
                fields(s,Set.of("code","description","capacityOz","rimMm","dimensions","packaging","cartonCbm"));
                if(!codes.add(text(s.path("code"),200,true))) fail();
                text(s.path("description"),1000,true);
                for(String field:Set.of("dimensions","packaging","cartonCbm")) if(s.has(field))text(s.path(field),1000,false);
                for(String field:Set.of("capacityOz","rimMm")) if(s.has(field)&&(!s.path(field).isNumber()||!Double.isFinite(s.path(field).asDouble())||s.path(field).asDouble()<=0))fail();
            }
            if(p.has("sourceBlocks")) {
                if(!p.path("sourceBlocks").isArray())fail();
                var blocks=new java.util.ArrayList<JsonNode>();p.path("sourceBlocks").forEach(blocks::add);
                cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogBlocks.validate(blocks);
            }
            if(p.has("sourceId")&&(!p.path("sourceId").isIntegralNumber()||p.path("sourceId").asLong()<=0))fail();
            if(p.has("sourcePath")){String path=text(p.path("sourcePath"),500,true);if(!path.matches("/[a-zA-Z0-9/_-]+/?"))fail();}
            if(p.has("sourceModifiedAt"))try{java.time.Instant.parse(text(p.path("sourceModifiedAt"),40,true));}catch(java.time.format.DateTimeParseException e){fail();}
            if(p.has("sourceContentSha256")&&!text(p.path("sourceContentSha256"),64,true).matches("[a-f0-9]{64}"))fail();
            if(p.has("migrationNotes")){if(!p.path("migrationNotes").isArray()||p.path("migrationNotes").size()>100)fail();for(JsonNode n:p.path("migrationNotes"))text(n,4000,false);}
            if(p.has("links")){if(!p.path("links").isArray()||p.path("links").size()>500)fail();for(JsonNode link:p.path("links")){fields(link,Set.of("text","href"));text(link.path("text"),2000,false);String href=text(link.path("href"),2000,true);if(!href.startsWith("https://")&&!(href.startsWith("/")&&!href.startsWith("//")))fail();}}
            JsonNode notes=p.path("notes"); if(!notes.isArray()||notes.size()>50)fail();
            for(JsonNode note:notes)text(note,4000,false);
        }
    }
    public static void asset(String url) {
        if(url.contains("..")||url.contains("%")||url.contains("\\")||url.contains("?")||url.contains("#")||url.chars().anyMatch(c->c<32))fail();
        if(url.startsWith("/assets/"))return;
        // A structurally valid managed URL still needs tenant ownership validation in the service.
        try { URI uri=URI.create(url); if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || !uri.getPath().contains("/website-media/"))fail(); }
        catch(IllegalArgumentException error){fail();}
    }
    private static void fields(JsonNode node,Set<String> allowed) {
        if(node==null||!node.isObject())fail();
        node.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))fail();});
    }
    private static String text(JsonNode value,int max,boolean required) {
        if(!value.isTextual()||value.asText().length()>max||(required&&value.asText().isBlank())||value.asText().chars().anyMatch(c->c<32&&c!=10&&c!=13&&c!=9))fail();
        return value.asText();
    }
    private static void fail(){throw exception(PAGE_CONTENT_INVALID);}
}
