package cn.iocoder.yudao.module.seo.controller.app.blog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPageRespVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPreviewExchangeReqVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogPreviewSessionRespVO;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - BLOG 企业日志")
@RestController
@RequestMapping("/seo/blog")
@Validated
public class AppWebsiteBlogController {

    private static final String PREVIEW_SESSION_HEADER = "X-Blog-Preview-Session";

    @Resource
    private WebsiteBlogService blogService;

    @GetMapping("/public")
    @Operation(summary = "获得已发布企业日志列表")
    @PermitAll
    public CommonResult<AppWebsiteBlogPageRespVO> getPublishedPage(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "pageSize", defaultValue = "24") @Min(1) @Max(100) Integer pageSize) {
        return success(blogService.getPublishedPage(siteId, locale, page, pageSize));
    }

    @GetMapping("/public/{slug}")
    @Operation(summary = "获得已发布企业日志详情")
    @PermitAll
    public CommonResult<AppWebsiteBlogArticleRespVO> getPublishedArticle(
            @PathVariable("slug") @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(blogService.getPublishedArticle(siteId, locale, slug));
    }

    @PostMapping("/preview/exchange")
    @Operation(summary = "兑换 Blog 一次性预览凭证")
    @PermitAll
    public CommonResult<AppWebsiteBlogPreviewSessionRespVO> exchangePreviewTicket(
            @Valid @RequestBody AppWebsiteBlogPreviewExchangeReqVO reqVO,
            @RequestHeader(value = "Origin", required = false) String origin,
            HttpServletResponse response) {
        disablePreviewCaching(response);
        return success(blogService.exchangePreviewTicket(reqVO.getTicket(), origin));
    }

    @GetMapping("/preview")
    @Operation(summary = "通过短期只读会话预览未发布 Blog")
    @PermitAll
    public CommonResult<AppWebsiteBlogArticleRespVO> getPreview(
            @RequestHeader(PREVIEW_SESSION_HEADER)
            @Pattern(regexp = "^bps_[A-Za-z0-9_-]{43}$") String session,
            @RequestHeader(value = "Origin", required = false) String origin,
            HttpServletResponse response) {
        disablePreviewCaching(response);
        return success(blogService.getPreview(session, origin));
    }

    private static void disablePreviewCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

}
