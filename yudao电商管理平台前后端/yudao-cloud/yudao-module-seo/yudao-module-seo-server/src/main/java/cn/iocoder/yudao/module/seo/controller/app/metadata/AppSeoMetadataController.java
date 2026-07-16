package cn.iocoder.yudao.module.seo.controller.app.metadata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.app.metadata.vo.SeoPublicMetadataRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.service.metadata.SeoMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - SEO 元数据")
@RestController
@RequestMapping("/seo/metadata")
@Validated
public class AppSeoMetadataController {

    @Resource
    private SeoMetadataService metadataService;

    @GetMapping("/resolve")
    @Operation(summary = "解析已发布 SEO 元数据")
    @PermitAll
    public CommonResult<SeoPublicMetadataRespVO> resolve(@RequestParam("siteId") Long siteId,
                                                         @RequestParam("entityType") String entityType,
                                                         @RequestParam("entityId") Long entityId,
                                                         @RequestParam("locale") String locale) {
        SeoMetadataDO metadata = metadataService.getPublishedMetadata(siteId, entityType, entityId, locale);
        if (metadata == null) {
            return success(null);
        }
        SeoPublicMetadataRespVO response = new SeoPublicMetadataRespVO();
        response.setTitle(metadata.getSeoTitle());
        response.setDescription(metadata.getMetaDescription());
        response.setCanonicalUrl(metadata.getCanonicalUrl());
        response.setRobotsIndex(metadata.getRobotsIndex());
        response.setRobotsFollow(metadata.getRobotsFollow());
        response.setOgTitle(metadata.getOgTitle());
        response.setOgDescription(metadata.getOgDescription());
        response.setOgImage(metadata.getOgImage());
        response.setSchemaType(metadata.getSchemaType());
        response.setLocale(metadata.getLocale());
        response.setVersion(metadata.getVersion());
        return success(response);
    }

}
