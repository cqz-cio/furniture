package cn.iocoder.yudao.module.trade.service.fulfillment.support;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.AddShipmentLegCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.DispatchShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.UpsertPackageCommand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class FulfillmentDispatchHashing {

    private static final HexFormat HEX = HexFormat.of();

    private FulfillmentDispatchHashing() {
    }

    public static String hash(UpsertPackageCommand command) {
        StringBuilder value = new StringBuilder(256);
        append(value, command.getTenantId());
        append(value, command.getShipmentId());
        append(value, command.getExpectedVersion());
        append(value, text(command.getPackageNo()));
        append(value, vocabulary(command.getPackageType()));
        append(value, command.getCarrierId());
        append(value, text(command.getTrackingNumber()));
        append(value, decimal(command.getWeight()));
        append(value, vocabulary(command.getWeightUnit()));
        append(value, decimal(command.getLength()));
        append(value, decimal(command.getWidth()));
        append(value, decimal(command.getHeight()));
        append(value, vocabulary(command.getDimensionUnit()));
        return sha256(value);
    }

    public static String hash(AddShipmentLegCommand command) {
        StringBuilder value = new StringBuilder(256);
        append(value, command.getTenantId());
        append(value, command.getShipmentId());
        append(value, command.getExpectedVersion());
        append(value, command.getPackageId());
        append(value, command.getSequenceNo());
        append(value, vocabulary(command.getLegType()));
        append(value, command.getCarrierId());
        append(value, command.getProviderId());
        append(value, text(command.getServiceLevel()));
        append(value, text(command.getTrackingNumber()));
        append(value, text(command.getProNumber()));
        append(value, text(command.getBolNumber()));
        append(value, text(command.getOriginLocation()));
        append(value, text(command.getDestinationLocation()));
        return sha256(value);
    }

    public static String hash(DispatchShipmentCommand command) {
        return hashMarkReady(command.getTenantId(), command.getShipmentId(), command.getExpectedVersion());
    }

    public static String hashMarkReady(Long tenantId, Long shipmentId, Integer expectedVersion) {
        StringBuilder value = new StringBuilder(64);
        append(value, tenantId);
        append(value, shipmentId);
        append(value, expectedVersion);
        return sha256(value);
    }

    private static String sha256(StringBuilder value) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private static String text(String value) {
        return value == null ? null : value.trim();
    }

    private static String vocabulary(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

}
