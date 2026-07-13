package cn.iocoder.yudao.module.statistics.enums.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BehaviorEventTypeEnum {
    HOME_VIEW(1), PRODUCT_DETAIL_VIEW(2), ADD_TO_CART(3), CHECKOUT_START(4);
    private final int value;

    public static BehaviorEventTypeEnum of(Integer value) {
        if (value != null) for (BehaviorEventTypeEnum item : values()) if (item.value == value) return item;
        throw new IllegalArgumentException("unsupported behavior event type");
    }
}
