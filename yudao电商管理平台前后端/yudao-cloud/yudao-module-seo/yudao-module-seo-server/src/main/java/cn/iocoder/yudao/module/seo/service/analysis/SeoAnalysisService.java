package cn.iocoder.yudao.module.seo.service.analysis;

import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisCompareRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRerunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoKeywordAnalysisRespVO;
import jakarta.validation.Valid;

import java.util.List;

public interface SeoAnalysisService {

    Long runAnalysis(@Valid SeoAnalysisRunReqVO reqVO);

    SeoAnalysisRespVO getAnalysis(Long id);

    List<SeoKeywordAnalysisRespVO> getKeywords(Long analysisId);

    SeoKeywordAnalysisRespVO getKeyword(Long analysisId, Long keywordAnalysisId);

    Long rerunAnalysis(Long id, @Valid SeoAnalysisRerunReqVO reqVO);

    SeoAnalysisCompareRespVO compareAnalysis(Long currentAnalysisId, Long previousAnalysisId);

}
