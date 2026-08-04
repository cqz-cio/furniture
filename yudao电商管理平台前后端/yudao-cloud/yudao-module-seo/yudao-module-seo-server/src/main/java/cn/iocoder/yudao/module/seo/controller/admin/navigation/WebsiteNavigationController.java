package cn.iocoder.yudao.module.seo.controller.admin.navigation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationCategoryOptionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationDraftSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPublishReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationRestoreReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationRevisionRespVO;
import cn.iocoder.yudao.module.seo.service.navigation.WebsiteNavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 官网导航")
@RestController
@RequestMapping("/seo/navigation")
@Validated
public class WebsiteNavigationController {

    @Resource
    private WebsiteNavigationService navigationService;

    @GetMapping("/draft")
    @Operation(summary = "获得官网导航草稿；没有草稿时自动从线上版本创建")
    @PreAuthorize("@ss.hasPermission('seo:navigation:query')")
    public CommonResult<WebsiteNavigationDraftRespVO> getDraft(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(navigationService.getDraft(siteId, locale));
    }

    @GetMapping("/category-options")
    @Operation(summary = "刷新商品中心分类选项")
    @PreAuthorize("@ss.hasPermission('seo:navigation:query')")
    public CommonResult<List<WebsiteNavigationCategoryOptionRespVO>> getCategoryOptions(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(navigationService.getCategoryOptions(siteId, locale));
    }

    @PutMapping("/draft")
    @Operation(summary = "保存官网导航草稿")
    @PreAuthorize("@ss.hasPermission('seo:navigation:update')")
    public CommonResult<Boolean> saveDraft(@Valid @RequestBody WebsiteNavigationDraftSaveReqVO reqVO) {
        navigationService.saveDraft(reqVO);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布官网导航")
    @PreAuthorize("@ss.hasPermission('seo:navigation:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody WebsiteNavigationPublishReqVO reqVO) {
        navigationService.publish(reqVO);
        return success(true);
    }

    @GetMapping("/history")
    @Operation(summary = "获得官网导航发布历史")
    @PreAuthorize("@ss.hasPermission('seo:navigation:query')")
    public CommonResult<List<WebsiteNavigationRevisionRespVO>> getHistory(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(navigationService.getHistory(siteId, locale));
    }

    @PostMapping("/restore-draft")
    @Operation(summary = "把历史导航版本恢复为草稿")
    @PreAuthorize("@ss.hasPermission('seo:navigation:update')")
    public CommonResult<Boolean> restoreDraft(
            @Valid @RequestBody WebsiteNavigationRestoreReqVO reqVO) {
        navigationService.restoreDraft(reqVO);
        return success(true);
    }

    @PostMapping("/preview-ticket")
    @Operation(summary = "生成一次性官网预览凭证")
    @PreAuthorize("@ss.hasPermission('seo:navigation:preview')")
    public CommonResult<WebsiteNavigationPreviewTicketRespVO> createPreviewTicket(
            @Valid @RequestBody WebsiteNavigationPreviewTicketReqVO reqVO) {
        return success(navigationService.createPreviewTicket(reqVO));
    }

}
