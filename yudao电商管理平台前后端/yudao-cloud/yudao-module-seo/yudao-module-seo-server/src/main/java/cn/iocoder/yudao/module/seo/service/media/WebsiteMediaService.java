package cn.iocoder.yudao.module.seo.service.media;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.seo.controller.admin.media.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.media.WebsiteMediaDO;
import cn.iocoder.yudao.module.seo.dal.mysql.media.WebsiteMediaMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;
@Service @Validated
public class WebsiteMediaService {
    @Resource private WebsiteMediaMapper mapper;
    @Resource private SeoSiteConfigMapper siteMapper;
    @Resource private FileApi fileApi;
    @Value("${YUDAO_WEBSITE_MEDIA_PUBLIC_BASE_URL:${yudao.website-media.public-base-url:}}") private String publicBase;

    private Long tenant() {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var site = siteMapper.selectBySiteId(1L);
        if (site == null || !tenant.equals(site.getTenantId())) throw exception(PAGE_SITE_UNAVAILABLE);
        return tenant;
    }
    public PageResult<WebsiteMediaDO> page(@Valid WebsiteMediaPageReqVO request) {
        tenant(); return mapper.page(request);
    }
    public WebsiteMediaDO get(Long id) {
        Long tenant = tenant();
        WebsiteMediaDO row = mapper.selectById(id);
        if (row == null || !tenant.equals(row.getTenantId())) throw exception(MEDIA_NOT_EXISTS);
        return row;
    }
    public WebsiteMediaDO upload(MultipartFile file, String alt) {
        Long tenant = tenant();
        String base = publicOrigin(); // Fail before writing if public delivery is unconfigured.
        alt = Objects.toString(alt, "").strip();
        if (alt.length() > 240) throw exception(MEDIA_FILE_INVALID);
        var content = WebsiteMediaContent.read(file);
        String name = Objects.toString(file.getOriginalFilename(), "素材").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isBlank()) name = "素材." + content.extension();
        if (name.length() > 160) name = name.substring(0, 160);
        String directory = "website-media/" + tenant + "/" + UUID.randomUUID();
        String stored = fileApi.createFile(content.bytes(), UUID.randomUUID() + "." + content.extension(), directory, content.mime());
        String url = publicUrl(stored, base, tenant);
        var row = new WebsiteMediaDO().setName(name).setAlt(alt).setKind(content.mime().startsWith("image/") ? "image" : "document")
            .setMimeType(content.mime()).setSize((long) content.bytes().length).setUrl(url)
            .setWidth(content.width()).setHeight(content.height()).setArchived(false);
        row.setTenantId(tenant); mapper.insert(row); return row;
    }
    public void update(@Valid WebsiteMediaUpdateReqVO request) {
        get(request.getId());
        var row = new WebsiteMediaDO().setId(request.getId()).setName(request.getName().strip()).setAlt(request.getAlt().strip());
        mapper.updateById(row);
    }
    public void archive(Long id, boolean archived) {
        get(id); mapper.updateById(new WebsiteMediaDO().setId(id).setArchived(archived));
        // Library visibility only: immutable public objects remain for published pages and history.
    }
    public void validateImage(JsonNode image) {
        String url = image.path("url").asText();
        // URI.getPath decodes percent escapes: encoded managed URLs still require ownership.
        String path = Objects.toString(URI.create(url).getPath(), "");
        if (!path.contains("/website-media/")) return;
        Long tenant = tenant();
        var row = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WebsiteMediaDO>()
            .eq(WebsiteMediaDO::getTenantId, tenant).eq(WebsiteMediaDO::getUrl, url));
        if (row == null || !tenant.equals(row.getTenantId()) || !"image".equals(row.getKind()))
            throw exception(MEDIA_REFERENCE_INVALID);
    }
    private String publicOrigin() {
        try {
            URI uri = URI.create(publicBase);
            if (uri.getHost() == null || !Set.of("http", "https").contains(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !Set.of("", "/").contains(uri.getPath()))
                throw exception(MEDIA_STORAGE_UNAVAILABLE);
            return publicBase.replaceAll("/+$", "");
        } catch (IllegalArgumentException error) { throw exception(MEDIA_STORAGE_UNAVAILABLE); }
    }
    static String publicUrl(String stored, String base, Long tenant) {
        try {
            URI uri = URI.create(stored);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw exception(MEDIA_STORAGE_UNAVAILABLE);
            String path = uri.getRawPath();
            if (path.matches("/admin-api/infra/file/[0-9]+/get/website-media/" + tenant + "/[A-Za-z0-9/.-]+")
                    && !path.contains("..")) return base + path;
            if ("https".equals(uri.getScheme()) && path.contains("/website-media/" + tenant + "/")) return stored;
            throw exception(MEDIA_STORAGE_UNAVAILABLE);
        } catch (IllegalArgumentException error) { throw exception(MEDIA_STORAGE_UNAVAILABLE); }
    }
}
