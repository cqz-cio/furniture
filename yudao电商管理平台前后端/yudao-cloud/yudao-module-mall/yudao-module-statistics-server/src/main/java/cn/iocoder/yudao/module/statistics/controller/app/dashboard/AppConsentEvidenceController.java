package cn.iocoder.yudao.module.statistics.controller.app.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceIssueReqVO;
import cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo.AppConsentEvidenceRespVO;
import cn.iocoder.yudao.module.statistics.service.dashboard.ConsentEvidenceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics/consent")
@Validated
public class AppConsentEvidenceController {
    @Resource
    private ConsentEvidenceService consentEvidenceService;

    @PostMapping("/evidence")
    public CommonResult<AppConsentEvidenceRespVO> issue(
            @Valid @RequestBody AppConsentEvidenceIssueReqVO request) {
        return CommonResult.success(consentEvidenceService.issue(request, ServletUtils.getClientIP()));
    }

    @PostMapping("/withdraw")
    public CommonResult<Boolean> withdraw(
            @RequestHeader("x-analytics-consent-evidence") String evidence) {
        consentEvidenceService.withdraw(evidence);
        return CommonResult.success(true);
    }
}
