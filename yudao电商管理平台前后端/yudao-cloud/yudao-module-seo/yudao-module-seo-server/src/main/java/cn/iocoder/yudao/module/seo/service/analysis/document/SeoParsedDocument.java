package cn.iocoder.yudao.module.seo.service.analysis.document;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SeoParsedDocument {

    String filename;
    String extension;
    String contentType;
    long fileSize;
    int extractedCharacters;
    boolean truncated;
    String content;
    List<String> paragraphs;

}
