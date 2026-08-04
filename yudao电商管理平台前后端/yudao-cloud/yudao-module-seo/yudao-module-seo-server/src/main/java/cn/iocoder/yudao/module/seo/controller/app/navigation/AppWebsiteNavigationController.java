package cn.iocoder.yudao.module.seo.controller.app.navigation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationPreviewExchangeReqVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationPreviewSessionRespVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import cn.iocoder.yudao.module.seo.service.navigation.WebsiteNavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 官网导航")
@RestController
@RequestMapping("/seo/navigation")
@Validated
public class AppWebsiteNavigationController {

    private static final String PREVIEW_SESSION_HEADER = "X-Website-Preview-Session";

    @Resource
    private WebsiteNavigationService navigationService;

    @GetMapping("/public")
    @Operation(summary = "获得已发布官网导航")
    @PermitAll
    public CommonResult<AppWebsiteNavigationRespVO> getPublished(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(navigationService.getPublished(siteId, locale));
    }

    @PostMapping("/preview/exchange")
    @Operation(summary = "将一次性预览凭证兑换为短期只读会话")
    @PermitAll
    public CommonResult<AppWebsiteNavigationPreviewSessionRespVO> exchangePreviewTicket(
            @Valid @RequestBody AppWebsiteNavigationPreviewExchangeReqVO reqVO,
            @RequestHeader(value = "Origin", required = false) String origin,
            HttpServletResponse response) {
        disablePreviewCaching(response);
        return success(navigationService.exchangePreviewTicket(reqVO.getTicket(), origin));
    }

    @GetMapping("/preview")
    @Operation(summary = "通过短期只读会话获得未发布导航")
    @PermitAll
    public CommonResult<AppWebsiteNavigationRespVO> getPreview(
            @RequestHeader(PREVIEW_SESSION_HEADER)
            @Pattern(regexp = "^ps_[A-Za-z0-9_-]{43}$") String session,
            @RequestHeader(value = "Origin", required = false) String origin,
            HttpServletResponse response) {
        disablePreviewCaching(response);
        return success(navigationService.getPreview(session, origin));
    }

    private static void disablePreviewCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

}
