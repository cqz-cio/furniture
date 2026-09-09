package cn.iocoder.yudao.module.seo.controller.admin.code;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.code.vo.*;
import cn.iocoder.yudao.module.seo.service.code.WebsiteCodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 网站统计与广告代码")
@RestController
@RequestMapping("/seo/website-code")
@Validated
public class WebsiteCodeController {
    @Resource private WebsiteCodeService service;

    @GetMapping("/draft")
    @PreAuthorize("@ss.hasPermission('seo:website-code:query')")
    public CommonResult<WebsiteCodeRespVO> getDraft(@RequestParam("siteId") @Min(1) Long siteId) {
        return success(service.getDraft(siteId));
    }

    @PutMapping("/draft")
    @PreAuthorize("@ss.hasPermission('seo:website-code:update')")
    public CommonResult<WebsiteCodeRespVO> save(@Valid @RequestBody WebsiteCodeSaveReqVO request) {
        return success(service.saveDraft(request));
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('seo:website-code:publish')")
    public CommonResult<WebsiteCodeRespVO> publish(@Valid @RequestBody WebsiteCodeVersionReqVO request) {
        return success(service.publish(request));
    }

    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('seo:website-code:query')")
    public CommonResult<List<WebsiteCodeHistoryRespVO>> history(@RequestParam("siteId") @Min(1) Long siteId) {
        return success(service.getHistory(siteId));
    }

    @PostMapping("/restore-draft")
    @PreAuthorize("@ss.hasPermission('seo:website-code:update')")
    public CommonResult<WebsiteCodeRespVO> restore(@Valid @RequestBody WebsiteCodeRestoreReqVO request) {
        return success(service.restoreDraft(request));
    }
}
