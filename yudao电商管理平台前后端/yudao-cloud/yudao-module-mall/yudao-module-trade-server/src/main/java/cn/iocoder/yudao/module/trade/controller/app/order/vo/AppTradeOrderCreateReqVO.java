package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Schema(description = "用户 App - 交易订单创建 Request VO")
@Data
public class AppTradeOrderCreateReqVO extends AppTradeOrderSettlementReqVO {

    private static final Set<String> SUPPORTED_ADDRESS_VERIFICATION_SOURCES = new HashSet<>(Arrays.asList(
            "google-address-validation", "remote-address-verification", "local-postal-region",
            "backend-address-verification"));
    private static final Set<String> SUPPORTED_ADDRESS_SOURCES = new HashSet<>(Arrays.asList("new", "saved"));
    private static final Set<String> SUPPORTED_ADDRESS_VERIFICATION_STATUSES = new HashSet<>(Arrays.asList(
            "verified", "suggested", "unverified"));
    private static final Set<String> SUPPORTED_ADDRESS_VERIFICATION_CHOICES = new HashSet<>(Arrays.asList(
            "original", "suggested"));

    @Schema(description = "备注", example = "这个是我的订单哟")
    private String remark;

    @Schema(description = "Address verification audit payload")
    private Map<String, Object> addressVerification;

    @AssertTrue(message = "配送方式不能为空")
    @JsonIgnore
    public boolean isDeliveryTypeNotNull() {
        return getDeliveryType() != null;
    }

    @AssertTrue(message = "快递订单必须包含用户确认后的地址核对记录")
    @JsonIgnore
    public boolean isConfirmedAddressVerificationForExpress() {
        if (!DeliveryTypeEnum.EXPRESS.getType().equals(getDeliveryType())) {
            return true;
        }
        return hasAddressVerificationText("source")
                && hasAddressVerificationText("addressSource")
                && hasAddressVerificationText("status")
                && hasAddressVerificationText("choice")
                && hasAddressVerificationText("confirmedAt")
                && hasSupportedAddressVerificationValue("source", SUPPORTED_ADDRESS_VERIFICATION_SOURCES)
                && hasSupportedAddressVerificationValue("addressSource", SUPPORTED_ADDRESS_SOURCES)
                && hasSupportedAddressVerificationValue("status", SUPPORTED_ADDRESS_VERIFICATION_STATUSES)
                && hasSupportedAddressVerificationValue("choice", SUPPORTED_ADDRESS_VERIFICATION_CHOICES)
                && hasConfirmedSelectedAddress();
    }

    private boolean hasAddressVerificationText(String key) {
        if (addressVerification == null) {
            return false;
        }
        Object value = addressVerification.get(key);
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private boolean hasSupportedAddressVerificationValue(String key, Set<String> supportedValues) {
        if (addressVerification == null) {
            return false;
        }
        Object value = addressVerification.get(key);
        return value != null && supportedValues.contains(String.valueOf(value).trim().toLowerCase());
    }

    private boolean hasConfirmedSelectedAddress() {
        if (addressVerification == null || !(addressVerification.get("selectedAddress") instanceof Map)) {
            return false;
        }
        Map<?, ?> selectedAddress = (Map<?, ?>) addressVerification.get("selectedAddress");
        return hasMapText(selectedAddress, "street")
                && hasMapText(selectedAddress, "city")
                && hasMapText(selectedAddress, "state")
                && hasMapText(selectedAddress, "postalCode");
    }

    private boolean hasMapText(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

}
