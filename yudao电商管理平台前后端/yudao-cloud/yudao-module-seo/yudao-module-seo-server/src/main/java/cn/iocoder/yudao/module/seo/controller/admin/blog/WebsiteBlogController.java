package cn.iocoder.yudao.module.seo.controller.admin.blog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticlePageReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPreviewTicketRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogPublishRecordRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSummaryRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogVersionReqVO;
import cn.iocoder.yudao.module.seo.service.blog.WebsiteBlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - BLOG 企业日志")
@RestController
@RequestMapping("/seo/blog")
@Validated
public class WebsiteBlogController {

    @Resource
    private WebsiteBlogService blogService;

    @GetMapping("/page")
    @Operation(summary = "获得企业日志分页")
    @PreAuthorize("@ss.hasPermission('seo:blog:query')")
    public CommonResult<PageResult<WebsiteBlogArticleRespVO>> getPage(
            @Valid WebsiteBlogArticlePageReqVO reqVO) {
        return success(blogService.getArticlePage(reqVO));
    }

    @GetMapping("/summary")
    @Operation(summary = "获得企业日志状态汇总")
    @PreAuthorize("@ss.hasPermission('seo:blog:query')")
    public CommonResult<WebsiteBlogSummaryRespVO> getSummary(
            @RequestParam("siteId") Long siteId,
            @RequestParam(value = "locale", defaultValue = "en") String locale) {
        return success(blogService.getSummary(siteId, locale));
    }

    @GetMapping("/get")
    @Operation(summary = "获得企业日志详情")
    @PreAuthorize("@ss.hasPermission('seo:blog:query')")
    public CommonResult<WebsiteBlogArticleRespVO> get(@RequestParam("id") Long id) {
        return success(blogService.getArticle(id));
    }

    @PostMapping("/create")
    @Operation(summary = "创建企业日志草稿")
    @PreAuthorize("@ss.hasPermission('seo:blog:create')")
    public CommonResult<Long> create(@Valid @RequestBody WebsiteBlogArticleSaveReqVO reqVO) {
        return success(blogService.createArticle(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "保存企业日志编辑稿")
    @PreAuthorize("@ss.hasPermission('seo:blog:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody WebsiteBlogArticleSaveReqVO reqVO) {
        blogService.updateArticle(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除企业日志")
    @PreAuthorize("@ss.hasPermission('seo:blog:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        blogService.deleteArticle(id);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布企业日志到官网")
    @PreAuthorize("@ss.hasPermission('seo:blog:publish')")
    public CommonResult<Boolean> publish(@Valid @RequestBody WebsiteBlogVersionReqVO reqVO) {
        blogService.publishArticle(reqVO);
        return success(true);
    }

    @PostMapping("/offline")
    @Operation(summary = "下线企业日志")
    @PreAuthorize("@ss.hasPermission('seo:blog:publish')")
    public CommonResult<Boolean> offline(@Valid @RequestBody WebsiteBlogVersionReqVO reqVO) {
        blogService.offlineArticle(reqVO);
        return success(true);
    }

    @GetMapping("/history")
    @Operation(summary = "获得企业日志发布记录")
    @PreAuthorize("@ss.hasPermission('seo:blog:query')")
    public CommonResult<List<WebsiteBlogPublishRecordRespVO>> getHistory(
            @RequestParam("articleId") Long articleId) {
        return success(blogService.getPublishHistory(articleId));
    }

    @PostMapping("/preview-ticket")
    @Operation(summary = "生成一次性 Blog 预览凭证")
    @PreAuthorize("@ss.hasPermission('seo:blog:preview')")
    public CommonResult<WebsiteBlogPreviewTicketRespVO> createPreviewTicket(
            @Valid @RequestBody WebsiteBlogVersionReqVO reqVO) {
        return success(blogService.createPreviewTicket(reqVO));
    }

}
