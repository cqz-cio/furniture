package cn.iocoder.yudao.module.seo.service.analysis.document;

import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoContentSnapshotReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoDocumentAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoParsedDocumentRespVO;
import cn.iocoder.yudao.module.seo.enums.SeoAnalysisSourceTypeEnum;
import cn.iocoder.yudao.module.seo.service.analysis.SeoAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@RequiredArgsConstructor
public class SeoDocumentAnalysisServiceImpl implements SeoDocumentAnalysisService {

    private final SeoDocumentParser documentParser;
    private final SeoAnalysisService analysisService;

    @Override
    public SeoParsedDocumentRespVO parse(MultipartFile file) {
        return toResponse(documentParser.parse(file));
    }

    @Override
    public Long run(SeoDocumentAnalysisRunReqVO reqVO) {
        SeoParsedDocument document = documentParser.parse(reqVO.getFile());
        SeoContentSnapshotReqVO content = new SeoContentSnapshotReqVO();
        content.setBody(document.getContent());
        content.setParagraphs(document.getParagraphs());

        SeoAnalysisRunReqVO analysisReq = new SeoAnalysisRunReqVO();
        analysisReq.setSiteId(reqVO.getSiteId());
        analysisReq.setEntityType(reqVO.getEntityType());
        analysisReq.setEntityId(reqVO.getEntityId());
        analysisReq.setLocale(reqVO.getLocale());
        analysisReq.setFocusKeyphrase(reqVO.getFocusKeyphrase());
        analysisReq.setRelatedKeyphrases(reqVO.getRelatedKeyphrases());
        analysisReq.setSourceType(SeoAnalysisSourceTypeEnum.DOCUMENT.getCode());
        analysisReq.setIdempotencyKey(reqVO.getIdempotencyKey());
        analysisReq.setContent(content);
        return analysisService.runAnalysis(analysisReq);
    }

    private static SeoParsedDocumentRespVO toResponse(SeoParsedDocument document) {
        SeoParsedDocumentRespVO response = new SeoParsedDocumentRespVO();
        response.setFilename(document.getFilename());
        response.setExtension(document.getExtension());
        response.setContentType(document.getContentType());
        response.setFileSize(document.getFileSize());
        response.setExtractedCharacters(document.getExtractedCharacters());
        response.setTruncated(document.isTruncated());
        response.setContent(document.getContent());
        return response;
    }

}
