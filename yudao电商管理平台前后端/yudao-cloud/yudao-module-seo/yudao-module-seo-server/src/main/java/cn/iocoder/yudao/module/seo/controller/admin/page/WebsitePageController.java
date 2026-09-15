package cn.iocoder.yudao.module.seo.controller.admin.page;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.service.page.WebsitePageService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/seo/page")
public class WebsitePageController {
    @Resource private WebsitePageService service;

    @GetMapping("/schema")
    @PreAuthorize("@ss.hasPermission('seo:page:query')")
    public CommonResult<JsonNode> schema(@Valid WebsitePageKeyReqVO key) { return success(service.getSchema(key)); }

    @GetMapping("/draft")
    @PreAuthorize("@ss.hasPermission('seo:page:query')")
    public CommonResult<WebsitePageRespVO> draft(@Valid WebsitePageKeyReqVO key) { return success(service.getDraft(key)); }

    @PostMapping("/initialize")
    @PreAuthorize("@ss.hasPermission('seo:page:update')")
    public CommonResult<WebsitePageRespVO> initialize(@Valid @RequestBody WebsitePageKeyReqVO key) {
        return success(service.initialize(key));
    }

    @PutMapping("/draft")
    @PreAuthorize("@ss.hasPermission('seo:page:update')")
    public CommonResult<WebsitePageRespVO> save(@Valid @RequestBody WebsitePageSaveReqVO request) {
        return success(service.saveDraft(request));
    }

    @PostMapping("/publish")
    @PreAuthorize("@ss.hasPermission('seo:page:publish')")
    public CommonResult<WebsitePageRespVO> publish(@Valid @RequestBody WebsitePageVersionReqVO request) {
        return success(service.publish(request));
    }

    @PostMapping("/restore-draft")
    @PreAuthorize("@ss.hasPermission('seo:page:update')")
    public CommonResult<WebsitePageRespVO> restore(@Valid @RequestBody WebsitePageRestoreReqVO request) {
        return success(service.restoreDraft(request));
    }

    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('seo:page:query')")
    public CommonResult<List<WebsitePageRespVO>> history(@Valid WebsitePageKeyReqVO key) {
        return success(service.getHistory(key));
    }

    @PostMapping("/preview-ticket")
    @PreAuthorize("@ss.hasPermission('seo:page:preview')")
    public CommonResult<Map<String, Object>> preview(@Valid @RequestBody WebsitePageVersionReqVO request) {
        return success(service.createPreviewTicket(request));
    }
}
