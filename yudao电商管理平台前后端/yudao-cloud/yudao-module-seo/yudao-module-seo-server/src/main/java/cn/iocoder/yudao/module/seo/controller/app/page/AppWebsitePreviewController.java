package cn.iocoder.yudao.module.seo.controller.app.page;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.service.page.WebsitePreviewService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
@RestController @RequestMapping("/seo/site-preview")
public class AppWebsitePreviewController {
    @Resource private WebsitePreviewService service;
    @PostMapping("/exchange") @PermitAll
    public CommonResult<Map<String, Object>> exchange(@RequestBody Map<String, String> request,
            @RequestHeader(value="Origin", required=false) String origin, HttpServletResponse response) {
        noCache(response);
        return CommonResult.success(service.exchange(request.get("ticket"), origin));
    }
    @GetMapping("/snapshot") @PermitAll
    public CommonResult<Map<String, Object>> get(@RequestHeader("X-Site-Preview-Session") String session,
            @RequestHeader(value="Origin", required=false) String origin, HttpServletResponse response) {
        noCache(response);
        return CommonResult.success(service.get(session, origin));
    }
    private void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
