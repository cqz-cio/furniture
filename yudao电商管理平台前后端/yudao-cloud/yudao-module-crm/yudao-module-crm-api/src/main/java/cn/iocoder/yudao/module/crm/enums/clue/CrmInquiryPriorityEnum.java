package cn.iocoder.yudao.module.crm.enums.clue;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 官网询盘优先级。
 */
@Getter
@AllArgsConstructor
public enum CrmInquiryPriorityEnum implements ArrayValuable<Integer> {

    HIGH(10, "高"),
    NORMAL(20, "普通"),
    LOW(30, "低");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmInquiryPriorityEnum::getPriority)
            .toArray(Integer[]::new);

    private final Integer priority;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
