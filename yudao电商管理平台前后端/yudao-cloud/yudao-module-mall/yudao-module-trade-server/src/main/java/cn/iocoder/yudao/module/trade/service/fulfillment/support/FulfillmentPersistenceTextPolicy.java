package cn.iocoder.yudao.module.trade.service.fulfillment.support;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_SENSITIVE_TEXT_NOT_ALLOWED;

/**
 * Rejects personal data, credentials, and raw payloads before fulfillment free text reaches persistence.
 */
public final class FulfillmentPersistenceTextPolicy {

    private static final int MAX_REFERENCE_LENGTH = 256;
    private static final Pattern EMAIL = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}"
                    + "(?![\\p{L}\\p{N}._%+-])");
    private static final Pattern NORTH_AMERICAN_PHONE = Pattern.compile(
            "(?iu)(?:\\b(?:tel|phone|mobile)\\s*(?::|=|\\s)\\s*(?:\\+?1[\\s().-]*)?"
                    + "\\(?\\d{3}\\)?[\\s.-]*"
                    + "\\d{3}[\\s.-]*\\d{4}(?!\\d)"
                    + "|(?<!\\d)\\+1[\\s().-]*\\(?\\d{3}\\)?[\\s.-]*\\d{3}[\\s.-]*\\d{4}(?!\\d)"
                    + "|(?<!\\d)(?:\\(\\d{3}\\)[\\s.-]*|\\d{3}[\\s.-]+)\\d{3}[\\s.-]+\\d{4}(?!\\d))");
    private static final Pattern STREET_ADDRESS = Pattern.compile(
            "(?iu)\\b\\d{1,6}\\s+(?:[\\p{L}\\p{N}.'-]+\\s+){0,6}"
                    + "(?:street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr|court|ct|way|"
                    + "highway|hwy|trail|trl|parkway|pkwy)\\b");
    private static final Pattern CREDENTIAL_ASSIGNMENT = Pattern.compile(
            "(?iu)\\b(?:api[\\s_-]?(?:key|token)|access[\\s_-]?token|auth(?:orization)?|"
                    + "client[\\s_-]?secret|credential|"
                    + "password|passwd|pwd|secret|token)\\b\\s*[:=]\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?iu)\\bbearer\\s+[a-z0-9._~+/=-]{3,}");
    private static final Pattern JWT = Pattern.compile(
            "(?i)(?<![a-z0-9_-])eyJ[a-z0-9_-]{5,}\\.[a-z0-9_-]{5,}\\.[a-z0-9_-]{5,}(?![a-z0-9_-])");
    private static final Pattern KNOWN_SECRET = Pattern.compile(
            "(?i)(?<![a-z0-9_-])(?:(?:AKIA|ASIA)[A-Z0-9]{16}|gh[pousr]_[a-z0-9]{20,}|"
                    + "sk_(?:live|test)_[a-z0-9]{12,}|sk-(?:proj-)?[a-z0-9_-]{16,})(?![a-z0-9_-])");
    private static final Pattern CREDENTIAL_URL = Pattern.compile("(?iu)\\bhttps?://[^\\s/@:]+:[^\\s/@]+@");
    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern UUID_REFERENCE = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern BASE64_CANDIDATE = Pattern.compile("[A-Za-z0-9_+/-]{24,}={0,2}");
    private static final Pattern FORM_PAYLOAD = Pattern.compile(
            "(?s)(?:^|[&;])[A-Za-z][A-Za-z0-9_.-]{0,63}=[^&;\\r\\n]+"
                    + "(?:[&;][A-Za-z][A-Za-z0-9_.-]{0,63}=[^&;\\r\\n]+)*$");
    private static final Pattern CSV_PAYLOAD = Pattern.compile(
            "(?s)^[^,\\r\\n]+,[^,\\r\\n]+(?:,[^\\r\\n]*|\\r?\\n.*)?$");
    private static final List<Pattern> FORBIDDEN_TEXT = List.of(EMAIL, NORTH_AMERICAN_PHONE, STREET_ADDRESS,
            CREDENTIAL_ASSIGNMENT, BEARER, JWT, KNOWN_SECRET, CREDENTIAL_URL);

    private FulfillmentPersistenceTextPolicy() {
    }

    public static String location(String value) {
        return normalizeAndValidate(value, false);
    }

    public static String description(String value) {
        return normalizeAndValidate(value, false);
    }

    public static String reference(String value) {
        return normalizeAndValidate(value, true);
    }

    private static String normalizeAndValidate(String value, boolean reference) {
        String normalized = TrackingEventCanonicalizer.normalize(value);
        if (normalized == null) {
            return null;
        }
        boolean forbidden = FORBIDDEN_TEXT.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
        if (reference) {
            forbidden = forbidden || normalized.length() > MAX_REFERENCE_LENGTH
                    || !SAFE_REFERENCE.matcher(normalized).matches()
                    || decodesToStructuredPayload(normalized);
        }
        if (forbidden) {
            throw exception(FULFILLMENT_SENSITIVE_TEXT_NOT_ALLOWED);
        }
        return normalized;
    }

    private static boolean decodesToStructuredPayload(String value) {
        if (UUID_REFERENCE.matcher(value).matches() || !BASE64_CANDIDATE.matcher(value).matches()) {
            return false;
        }
        String padded = padBase64(value);
        return decodedPayload(padded, Base64.getUrlDecoder()) || decodedPayload(padded, Base64.getDecoder());
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "=".repeat(4 - remainder);
    }

    private static boolean decodedPayload(String value, Base64.Decoder decoder) {
        try {
            byte[] decoded = decoder.decode(value);
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded)).toString();
            return mostlyPrintable(text) && looksLikeStructuredPayload(text);
        } catch (IllegalArgumentException | CharacterCodingException ignored) {
            return false;
        }
    }

    private static boolean mostlyPrintable(String value) {
        if (value.isEmpty()) {
            return false;
        }
        long printable = value.chars()
                .filter(character -> character == '\r' || character == '\n' || character == '\t'
                        || !Character.isISOControl(character))
                .count();
        return printable * 100 >= value.length() * 85L;
    }

    private static boolean looksLikeStructuredPayload(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return first == '{' || first == '[' || first == '<'
                || FORM_PAYLOAD.matcher(trimmed).matches()
                || CSV_PAYLOAD.matcher(trimmed).matches();
    }

}
