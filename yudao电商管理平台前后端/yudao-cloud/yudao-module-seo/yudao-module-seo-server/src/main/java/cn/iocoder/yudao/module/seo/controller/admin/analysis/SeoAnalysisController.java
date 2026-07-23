package cn.iocoder.yudao.module.seo.controller.admin.analysis;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisCompareRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRerunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoKeywordAnalysisRespVO;
import cn.iocoder.yudao.module.seo.service.analysis.SeoAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - SEO 逐关键词关联度分析")
@RestController
@RequestMapping("/seo/analysis")
@Validated
public class SeoAnalysisController {

    @Resource
    private SeoAnalysisService analysisService;

    @PostMapping("/run")
    @Operation(summary = "运行 SEO 逐关键词分析")
    @PreAuthorize("@ss.hasPermission('seo:analysis:run')")
    public CommonResult<Long> runAnalysis(@Valid @RequestBody SeoAnalysisRunReqVO reqVO) {
        return success(analysisService.runAnalysis(reqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 SEO 分析详情")
    @PreAuthorize("@ss.hasPermission('seo:analysis:query')")
    public CommonResult<SeoAnalysisRespVO> getAnalysis(@PathVariable("id") Long id) {
        return success(analysisService.getAnalysis(id));
    }

    @GetMapping("/{id}/keywords")
    @Operation(summary = "获取 SEO 分析的关键词摘要")
    @PreAuthorize("@ss.hasPermission('seo:analysis:query')")
    public CommonResult<List<SeoKeywordAnalysisRespVO>> getKeywords(@PathVariable("id") Long id) {
        return success(analysisService.getKeywords(id));
    }

    @GetMapping("/{id}/keywords/{keywordAnalysisId}")
    @Operation(summary = "获取单个关键词证据与建议")
    @PreAuthorize("@ss.hasPermission('seo:analysis:query')")
    public CommonResult<SeoKeywordAnalysisRespVO> getKeyword(
            @PathVariable("id") Long id,
            @PathVariable("keywordAnalysisId") Long keywordAnalysisId) {
        return success(analysisService.getKeyword(id, keywordAnalysisId));
    }

    @PostMapping("/{id}/rerun")
    @Operation(summary = "重新运行 SEO 分析并保留历史")
    @PreAuthorize("@ss.hasPermission('seo:analysis:run')")
    public CommonResult<Long> rerunAnalysis(@PathVariable("id") Long id,
                                            @Valid @RequestBody SeoAnalysisRerunReqVO reqVO) {
        return success(analysisService.rerunAnalysis(id, reqVO));
    }

    @GetMapping("/{id}/compare")
    @Operation(summary = "对比两次 SEO 分析")
    @PreAuthorize("@ss.hasPermission('seo:analysis:query')")
    public CommonResult<SeoAnalysisCompareRespVO> compareAnalysis(
            @PathVariable("id") Long id,
            @RequestParam(value = "previousAnalysisId", required = false) Long previousAnalysisId) {
        return success(analysisService.compareAnalysis(id, previousAnalysisId));
    }

}
