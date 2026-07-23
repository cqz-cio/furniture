package cn.iocoder.yudao.module.seo.service.analysis.document;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaSeoDocumentParserTest {

    private final TikaSeoDocumentParser parser = new TikaSeoDocumentParser(new SeoAnalysisProperties());

    @Test
    void parse_shouldSupportDocxPdfAndXlsx() throws Exception {
        assertParsed("product.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("solid wood dining table"));
        assertParsed("product.pdf", "application/pdf", pdf("solid wood dining table"));
        assertParsed("product.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx("solid wood dining table"));
    }

    @Test
    void parse_shouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.txt", "text/plain", "solid wood dining table".getBytes());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DOCX");
    }

    @Test
    void parse_shouldEnforceConfiguredSizeAndExtractionLimits() throws Exception {
        SeoAnalysisProperties analysisProperties = new SeoAnalysisProperties();
        analysisProperties.getDocument().setMaxFileSize(DataSize.ofBytes(1));
        TikaSeoDocumentParser sizeLimitedParser = new TikaSeoDocumentParser(analysisProperties);
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "product.docx", "application/octet-stream", docx("solid wood dining table"));
        assertThatThrownBy(() -> sizeLimitedParser.parse(oversized))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过允许大小");

        analysisProperties.getDocument().setMaxFileSize(DataSize.ofMegabytes(1));
        analysisProperties.getDocument().setMaxExtractedChars(10);
        SeoParsedDocument truncated = new TikaSeoDocumentParser(analysisProperties).parse(
                new MockMultipartFile("file", "product.docx", "application/octet-stream",
                        docx("solid wood dining table")));
        assertThat(truncated.isTruncated()).isTrue();
        assertThat(truncated.getContent()).hasSize(10);
        assertThat(truncated.getExtractedCharacters()).isGreaterThan(10);
    }

    private void assertParsed(String filename, String contentType, byte[] bytes) {
        SeoParsedDocument result = parser.parse(new MockMultipartFile(
                "file", filename, contentType, bytes));

        assertThat(result.getFilename()).isEqualTo(filename);
        assertThat(result.getContent()).containsIgnoringCase("solid wood dining table");
        assertThat(result.isTruncated()).isFalse();
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] xlsx(String text) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Products").createRow(0).createCell(0).setCellValue(text);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

}
