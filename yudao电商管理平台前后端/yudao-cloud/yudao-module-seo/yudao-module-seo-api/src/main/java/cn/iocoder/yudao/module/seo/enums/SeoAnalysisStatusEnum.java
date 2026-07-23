package cn.iocoder.yudao.module.seo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeoAnalysisStatusEnum {

    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    PARTIAL("PARTIAL"),
    FAILED("FAILED");

    private final String code;

}
