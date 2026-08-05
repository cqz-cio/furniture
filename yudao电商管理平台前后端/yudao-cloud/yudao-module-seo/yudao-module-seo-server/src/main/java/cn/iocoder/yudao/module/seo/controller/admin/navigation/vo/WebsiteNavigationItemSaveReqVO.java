package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航项保存 Request VO")
@Data
public class WebsiteNavigationItemSaveReqVO {

    @Schema(description = "稳定节点标识；PAGE/CATEGORY 兼容旧客户端时可由服务端生成")
    @Size(max = 64)
    private String itemKey;

    @Schema(description = "父节点稳定标识；空字符串代表一级导航")
    @Size(max = 64)
    private String parentItemKey;

    @Schema(description = "类型：PAGE、CATEGORY、DIRECTORY、ROUTE 或 FILTER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String itemType;

    @Schema(description = "固定页面标识")
    private String pageKey;

    @Schema(description = "商品分类编号")
    private Long categoryId;

    @Schema(description = "安全目标标识，由服务端映射真实前台地址")
    @Size(max = 64)
    private String targetKey;

    @Schema(description = "前台显示名称；VANZ 分类项可在确认后同步回商品中心")
    @Size(max = 64)
    private String label;

    @Schema(description = "是否把分类项名称同步回商品中心")
    private Boolean syncCategoryName;

    @Schema(description = "排序值，越小越靠前", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Integer sort;

    @Schema(description = "是否在官网显示", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean visible;

    @Schema(description = "打开方式：_self 或 _blank")
    @Size(max = 16)
    private String openMode;

    @Schema(description = "视觉样式：DEFAULT 或 SALE")
    @Size(max = 32)
    private String styleVariant;

}
