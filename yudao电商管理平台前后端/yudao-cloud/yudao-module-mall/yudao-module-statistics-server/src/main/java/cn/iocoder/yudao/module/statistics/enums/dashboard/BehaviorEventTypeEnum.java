package cn.iocoder.yudao.module.statistics.enums.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BehaviorEventTypeEnum {
    HOME_VIEW(1),
    PRODUCT_DETAIL_VIEW(2),
    ADD_TO_CART(3),
    CHECKOUT_START(4),
    PAGE_VIEW(5),
    CATEGORY_VIEW(6),
    ADD_TO_QUOTE(7),
    OPEN_QUOTE_LIST(8),
    INQUIRY_START(9),
    INQUIRY_SUBMIT_SUCCESS(10),
    CONTACT_CLICK(11),
    CATALOGUE_DOWNLOAD(12);
    private final int value;

    public static BehaviorEventTypeEnum of(Integer value) {
        if (value != null) for (BehaviorEventTypeEnum item : values()) if (item.value == value) return item;
        throw new IllegalArgumentException("unsupported behavior event type");
    }
}
