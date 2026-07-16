package cn.iocoder.yudao.module.trade.service.fulfillment.support;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.time.Instant;

public final class FulfillmentHashing {

    private static final HexFormat HEX = HexFormat.of();

    private FulfillmentHashing() {
    }

    public static String hmacSha256Hex(String secret, String value) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Fulfillment idempotency HMAC key is not configured");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public static String sha256Command(CreateShipmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Shipment command must not be null");
        }
        StringBuilder canonical = new StringBuilder(256);
        append(canonical, command.getTenantId());
        append(canonical, command.getOrderId());
        append(canonical, command.getShipmentType() == null ? null : command.getShipmentType().name());
        append(canonical, normalizeCountry(command.getOriginCountry()));
        append(canonical, normalizeCountry(command.getDestinationCountry()));
        append(canonical, normalizeText(command.getOriginTimezone()));
        append(canonical, normalizeText(command.getDestinationTimezone()));
        append(canonical, command.getWarehouseId());
        append(canonical, command.getProviderId());
        List<CreateShipmentItemCommand> items = command.getItems() == null ? List.of() : command.getItems().stream()
                .sorted(Comparator.comparing(CreateShipmentItemCommand::getOrderItemId,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(CreateShipmentItemCommand::getSkuId,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(item -> normalizeQuantity(item.getQuantity()),
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        append(canonical, items.size());
        for (CreateShipmentItemCommand item : items) {
            append(canonical, item.getOrderItemId());
            append(canonical, item.getSkuId());
            append(canonical, normalizeQuantity(item.getQuantity()));
        }
        return sha256Hex(canonical.toString());
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256ManualTracking(Long tenantId, Long shipmentId, Long packageId, Long shipmentLegId,
                                               String requestedStatus, Instant occurredAt,
                                               Integer expectedShipmentVersion, Long operatorId, String reason) {
        StringBuilder canonical = new StringBuilder(256);
        append(canonical, tenantId);
        append(canonical, shipmentId);
        append(canonical, packageId);
        append(canonical, shipmentLegId);
        append(canonical, requestedStatus);
        append(canonical, occurredAt == null ? null : TrackingEventCanonicalizer.truncateToMicros(occurredAt));
        append(canonical, expectedShipmentVersion);
        append(canonical, operatorId);
        append(canonical, reason == null ? null : reason.trim());
        return sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private static String normalizeCountry(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeQuantity(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

}
