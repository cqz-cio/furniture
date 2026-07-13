package cn.iocoder.yudao.module.statistics.controller.app.dashboard;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppBehaviorEventTrackReqVO;
import cn.iocoder.yudao.module.statistics.service.dashboard.BehaviorEventService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
@RestController @RequestMapping("/statistics/behavior") @Validated
public class AppBehaviorEventController {
    @Resource private BehaviorEventService behaviorEventService;
    @PostMapping("/track")
    public CommonResult<Boolean> track(@Valid @RequestBody AppBehaviorEventTrackReqVO request,
            @RequestHeader("x-analytics-visitor-id") String visitorId,
            @RequestHeader("x-analytics-session-id") String sessionId,
            @RequestHeader(value="x-analytics-consent-evidence",required=false) String consentEvidence) {
        behaviorEventService.trackPublic(request, visitorId, sessionId, consentEvidence, ServletUtils.getClientIP(), SecurityFrameworkUtils.getLoginUserId());
        return CommonResult.success(true);
    }
}
