package cn.iocoder.yudao.module.crm.enums.clue;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 官网询盘销售阶段。处理状态负责日常待办，销售阶段负责描述商机所处位置。
 */
@Getter
@AllArgsConstructor
public enum CrmInquirySalesStageEnum implements ArrayValuable<Integer> {

    NEW(0, "新询盘"),
    QUALIFYING(10, "需求确认"),
    QUOTING(20, "报价中"),
    SAMPLE(30, "打样中"),
    NEGOTIATION(40, "商务谈判"),
    WON(50, "已赢单"),
    LOST(60, "已丢单");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmInquirySalesStageEnum::getStage)
            .toArray(Integer[]::new);

    private final Integer stage;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
