package cn.iocoder.yudao.module.crm.enums.clue;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 官网询盘处理状态。
 */
@Getter
@AllArgsConstructor
public enum CrmInquiryProcessStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待处理"),
    PROCESSING(10, "处理中"),
    PROCESSED(20, "已处理"),
    INVALID(30, "无效询盘");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmInquiryProcessStatusEnum::getStatus)
            .toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isFinished(Integer status) {
        return PROCESSED.status.equals(status) || INVALID.status.equals(status);
    }

    public static String getNameByStatus(Integer status) {
        return Arrays.stream(values())
                .filter(item -> item.status.equals(status))
                .map(CrmInquiryProcessStatusEnum::getName)
                .findFirst()
                .orElse(null);
    }

}
