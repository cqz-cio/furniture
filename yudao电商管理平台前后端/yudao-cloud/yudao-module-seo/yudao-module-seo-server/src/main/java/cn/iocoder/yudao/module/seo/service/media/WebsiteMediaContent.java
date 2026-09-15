package cn.iocoder.yudao.module.seo.service.media;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;
/** Validate actual bytes and re-encode images; client MIME and filenames are not trusted. */
public record WebsiteMediaContent(byte[] bytes, String mime, String extension, Integer width, Integer height) {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static WebsiteMediaContent read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw exception(MEDIA_FILE_INVALID);
        try (InputStream input = file.getInputStream()) {
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length == 0 || data.length > MAX_BYTES) throw exception(MEDIA_FILE_INVALID);
            String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf") && data.length >= 12
                    && new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                    && new String(data, Math.max(0, data.length - 1024), Math.min(1024, data.length), StandardCharsets.ISO_8859_1).contains("%%EOF"))
            {
                try (var pdf = org.apache.pdfbox.Loader.loadPDF(data)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 1000)
                        throw exception(MEDIA_FILE_INVALID);
                    return new WebsiteMediaContent(data, "application/pdf", "pdf", null, null);
                }
            }
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw exception(MEDIA_FILE_INVALID);
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!Set.of("jpeg", "png").contains(format)) throw exception(MEDIA_FILE_INVALID);
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 12000 || height > 12000 || (long) width * height > 25000000)
                        throw exception(MEDIA_FILE_INVALID);
                    var image = reader.read(0);
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, format, output) || output.size() > MAX_BYTES) throw exception(MEDIA_FILE_INVALID);
                    return new WebsiteMediaContent(output.toByteArray(), "image/" + format, format.equals("jpeg") ? "jpg" : "png", width, height);
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException error) { throw exception(MEDIA_FILE_INVALID); }
    }
}
