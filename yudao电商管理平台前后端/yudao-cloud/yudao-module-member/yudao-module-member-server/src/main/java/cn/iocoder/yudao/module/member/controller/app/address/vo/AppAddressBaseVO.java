package cn.iocoder.yudao.module.member.controller.app.address.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

// TODO 芋艿：example 缺失
/**
* 用户收件地址 Base VO，提供给添加、修改、详细的子 VO 使用
* 如果子 VO 存在差异的字段，请不要添加到这里，影响 Swagger 文档生成
*/
@Data
public class AppAddressBaseVO {

    @Schema(description = "收件人名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收件人名称不能为空")
    private String name;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "手机号不能为空")
    private String mobile;

    @Schema(description = "地区编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "地区编号不能为空")
    private Long areaId;

    @Schema(description = "收件详细地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收件详细地址不能为空")
    private String detailAddress;

    @Schema(description = "是否默认地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否默认地址不能为空")
    private Boolean defaultStatus;

    @Schema(description = "Address verification audit payload from checkout confirmation")
    private Map<String, Object> addressVerification;

    @AssertTrue(message = "Address verification audit must include confirmed selected address")
    @JsonIgnore
    public boolean isConfirmedAddressVerification() {
        if (addressVerification == null) {
            return true;
        }
        return hasAddressVerificationText("source")
                && hasAddressVerificationText("addressSource")
                && hasAddressVerificationText("status")
                && hasAddressVerificationText("choice")
                && hasAddressVerificationText("confirmedAt")
                && hasConfirmedSelectedAddress();
    }

    private boolean hasAddressVerificationText(String key) {
        Object value = addressVerification.get(key);
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private boolean hasConfirmedSelectedAddress() {
        if (!(addressVerification.get("selectedAddress") instanceof Map)) {
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
