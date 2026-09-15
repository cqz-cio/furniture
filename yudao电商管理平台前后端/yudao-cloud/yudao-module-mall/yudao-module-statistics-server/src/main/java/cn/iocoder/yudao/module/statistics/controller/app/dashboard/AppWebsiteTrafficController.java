package cn.iocoder.yudao.module.statistics.controller.app.dashboard;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
import cn.iocoder.yudao.module.statistics.service.dashboard.*;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/statistics/website-traffic")
public class AppWebsiteTrafficController {
    @Resource private WebsiteTrafficService website;
    @Resource private BehaviorEventService behavior;
    @GetMapping("/config") @PermitAll
    public CommonResult<Map<String, Object>> config() { return CommonResult.success(website.config()); }
    @PostMapping("/track") @PermitAll
    public CommonResult<Boolean> track(@Valid @RequestBody AppBehaviorEventTrackReqVO request,
            @RequestHeader("x-analytics-visitor-id") String visitor,
            @RequestHeader("x-analytics-session-id") String session,
            @RequestHeader("x-analytics-consent-evidence") String consent) {
        if (!website.enabled()) throw new IllegalStateException("tracking is disabled");
        if (!Integer.valueOf(5).equals(request.getEventType()) || request.getSpuId() != null || request.getSkuId() != null
                || !visitor.matches("[a-zA-Z0-9-]{16,64}") || !session.matches("[a-zA-Z0-9-]{16,64}")
                || !request.getPagePath().matches("/(?:[a-z0-9-]+/?)*") || request.getPagePath().contains("preview"))
            throw new IllegalArgumentException("invalid website page view");
        behavior.trackPublic(request, visitor, session, consent, ServletUtils.getClientIP(), null);
        return CommonResult.success(true);
    }
}
