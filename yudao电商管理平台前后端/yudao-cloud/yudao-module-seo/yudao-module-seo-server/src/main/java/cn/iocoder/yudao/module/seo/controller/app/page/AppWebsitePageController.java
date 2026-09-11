package cn.iocoder.yudao.module.seo.controller.app.page;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/seo/page")
public class AppWebsitePageController {
    @Resource private WebsitePageService service;
    public record ExchangeRequest(@NotBlank @Pattern(regexp = "ppv_[A-Za-z0-9_-]{43}") String ticket) {}

    @GetMapping("/public") @PermitAll
    public CommonResult<WebsitePageRespVO> published(@Valid WebsitePageKeyReqVO key, HttpServletResponse response) {
        noCache(response, false);
        return success(service.getPublished(key));
    }

    @PostMapping("/preview/exchange") @PermitAll
    public CommonResult<Map<String, Object>> exchange(@Valid @RequestBody ExchangeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        noCache(response, true);
        return success(service.exchangePreviewTicket(body.ticket(), requestOrigin(request)));
    }

    @GetMapping("/preview") @PermitAll
    public CommonResult<WebsitePageRespVO> preview(@RequestHeader("X-Page-Preview-Session") String session,
            HttpServletRequest request, HttpServletResponse response) {
        noCache(response, true);
        return success(service.getPreview(session, requestOrigin(request)));
    }

    private static String requestOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        return origin != null ? origin : request.getHeader("Referer");
    }
    private static void noCache(HttpServletResponse response, boolean preview) {
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        if (preview) response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
