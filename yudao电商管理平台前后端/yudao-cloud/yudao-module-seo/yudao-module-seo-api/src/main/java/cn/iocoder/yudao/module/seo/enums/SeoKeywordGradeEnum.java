package cn.iocoder.yudao.module.seo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeoKeywordGradeEnum {

    HIGH("HIGH", "高度相关", 80),
    MEDIUM("MEDIUM", "基本相关", 60),
    WEAK("WEAK", "关联较弱", 40),
    LOW("LOW", "关联度低", 0);

    private final String code;
    private final String label;
    private final int minimumPercent;

    public static SeoKeywordGradeEnum fromPercent(int percent) {
        if (percent >= HIGH.minimumPercent) {
            return HIGH;
        }
        if (percent >= MEDIUM.minimumPercent) {
            return MEDIUM;
        }
        if (percent >= WEAK.minimumPercent) {
            return WEAK;
        }
        return LOW;
    }

}
