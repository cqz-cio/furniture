package cn.iocoder.yudao.module.seo.service.analysis.document;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.DOCUMENT_FILE_EMPTY;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.DOCUMENT_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.DOCUMENT_PARSE_FAILED;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.DOCUMENT_TYPE_UNSUPPORTED;

@Component
@Slf4j
public class TikaSeoDocumentParser implements SeoDocumentParser {

    private final SeoAnalysisProperties.Document properties;

    public TikaSeoDocumentParser(SeoAnalysisProperties analysisProperties) {
        this.properties = analysisProperties.getDocument();
    }

    @Override
    public SeoParsedDocument parse(MultipartFile file) {
        validate(file);
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extensionOf(filename);
        try {
            byte[] bytes = file.getBytes();
            ByteArrayResource resource = new NamedByteArrayResource(bytes, filename);
            List<Document> documents = new TikaDocumentReader(resource).get();
            String extracted = documents == null ? "" : documents.stream()
                    .map(Document::getText)
                    .filter(StrUtil::isNotBlank)
                    .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n\n" + right);
            String normalized = normalize(extracted);
            if (normalized.isBlank()) {
                throw exception(DOCUMENT_PARSE_FAILED);
            }
            int extractedCharacters = normalized.length();
            int maxCharacters = Math.max(1, properties.getMaxExtractedChars());
            boolean truncated = extractedCharacters > maxCharacters;
            String content = truncated ? normalized.substring(0, maxCharacters) : normalized;
            return SeoParsedDocument.builder()
                    .filename(filename)
                    .extension(extension)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .extractedCharacters(extractedCharacters)
                    .truncated(truncated)
                    .content(content)
                    .paragraphs(paragraphs(content))
                    .build();
        } catch (IOException ex) {
            log.warn("[parse][filename({}) 读取 SEO 分析文档失败]", filename, ex);
            throw exception(DOCUMENT_PARSE_FAILED);
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("[parse][filename({}) 解析 SEO 分析文档失败: {}]",
                    filename, ex.getClass().getSimpleName());
            throw exception(DOCUMENT_PARSE_FAILED);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw exception(DOCUMENT_FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw exception(DOCUMENT_FILE_TOO_LARGE);
        }
        String extension = extensionOf(file.getOriginalFilename());
        boolean allowed = properties.getAllowedExtensions().stream()
                .filter(StrUtil::isNotBlank)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(extension::equals);
        if (!allowed) {
            throw exception(DOCUMENT_TYPE_UNSUPPORTED);
        }
    }

    private static String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename == null ? "" : filename);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String source) {
        if (source == null) {
            return "";
        }
        return Arrays.stream(source.replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .replace("\u0000", "")
                        .split("\n", -1))
                .map(line -> line.replaceAll("[\\t ]+$", ""))
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right)
                .trim();
    }

    private static List<String> paragraphs(String content) {
        return Arrays.stream(content.split("(?:\\n\\s*){2,}"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .limit(1000)
                .map(value -> value.length() <= 10000 ? value : value.substring(0, 10000))
                .toList();
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

    }

}
