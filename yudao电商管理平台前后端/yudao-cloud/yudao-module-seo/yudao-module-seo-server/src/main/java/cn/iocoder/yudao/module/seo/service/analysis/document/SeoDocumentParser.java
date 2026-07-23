package cn.iocoder.yudao.module.seo.service.analysis.document;

import org.springframework.web.multipart.MultipartFile;

public interface SeoDocumentParser {

    SeoParsedDocument parse(MultipartFile file);

}
