package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class DashboardStageOverviewRespVO {
    private Boolean cohortAligned = false;
    private String explanation = "Stage sizes use different dedupe scopes and are not a cohort funnel.";
    private List<Item> items = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class Item {
        private String stage;
        private Long value;
        private String unit;
        private String dedupeScope;
        private String applicability;
    }
}
