package cn.iocoder.yudao.module.trade.service.fulfillment.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class TrackingEventCanonicalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter MICROSECOND_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(java.time.ZoneOffset.UTC);

    private TrackingEventCanonicalizer() {
    }

    public static String stableHash(String carrierCode, String trackingNumber, String providerStatus,
                                    Instant occurredAt, String location, String description) {
        String canonical = normalizeUpper(carrierCode) + "\n"
                + normalize(trackingNumber) + "\n"
                + normalizeUpper(providerStatus) + "\n"
                + MICROSECOND_UTC.format(truncateToMicros(Objects.requireNonNull(occurredAt, "occurredAt"))) + "\n"
                + nullToEmpty(normalize(location)) + "\n"
                + nullToEmpty(normalize(description));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static Instant truncateToMicros(Instant value) {
        return Objects.requireNonNull(value, "value").truncatedTo(ChronoUnit.MICROS);
    }

    public static String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return WHITESPACE.matcher(normalized).replaceAll(" ");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
