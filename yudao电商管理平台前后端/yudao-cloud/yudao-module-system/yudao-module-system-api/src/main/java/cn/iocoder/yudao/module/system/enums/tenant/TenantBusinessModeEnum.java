package cn.iocoder.yudao.module.system.enums.tenant;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 租户业务模式枚举
 */
@Getter
@RequiredArgsConstructor
public enum TenantBusinessModeEnum implements ArrayValuable<String> {

    B2C("B2C", true),
    B2B("B2B", false);

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(TenantBusinessModeEnum::getCode)
            .toArray(String[]::new);

    /**
     * 业务模式编码
     */
    private final String code;
    /**
     * 是否启用库存管理界面
     */
    private final boolean inventoryEnabled;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static TenantBusinessModeEnum of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("非法租户业务模式：" + code));
    }

}
