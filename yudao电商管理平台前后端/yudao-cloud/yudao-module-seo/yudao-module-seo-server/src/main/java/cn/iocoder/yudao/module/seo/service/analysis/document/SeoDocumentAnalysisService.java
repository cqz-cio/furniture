package cn.iocoder.yudao.module.seo.service.analysis.document;

import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoDocumentAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoParsedDocumentRespVO;
import org.springframework.web.multipart.MultipartFile;

public interface SeoDocumentAnalysisService {

    SeoParsedDocumentRespVO parse(MultipartFile file);

    Long run(SeoDocumentAnalysisRunReqVO reqVO);

}
