package cn.iocoder.yudao.module.product.controller.admin.spu.vo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical furniture product-detail save contract.
 *
 * <p>The database may continue to store JSON, but admin requests enter this typed boundary
 * before they are converted to a map for {@code product_spu.detail_config}. Empty strings and
 * empty arrays intentionally mean "do not display". {@code productType} is deliberately absent:
 * the selected P2 {@code categoryId} is the only writable product-type identity.</p>
 */
@Schema(description = "管理后台 - 家具商品详情配置")
@Data
public class ProductDetailConfigSaveReqVO {

    @Size(max = 64, message = "Item No. 不能超过 64 个字符")
    private String itemNo;
    @Size(max = 255, message = "Material 不能超过 255 个字符")
    private String material;
    @Valid
    private Dimension dimension;
    @Size(max = 255, message = "Color 不能超过 255 个字符")
    private String color;
    @Size(max = 255, message = "Finish 不能超过 255 个字符")
    private String finish;
    @Size(max = 255, message = "Service 不能超过 255 个字符")
    private String service;
    @Size(max = 255, message = "Sample 不能超过 255 个字符")
    private String sample;
    @Size(max = 500, message = "Packing 不能超过 500 个字符")
    private String packing;
    @Size(max = 255, message = "Collection 不能超过 255 个字符")
    private String collection;
    @Size(max = 1000, message = "Hero note 不能超过 1000 个字符")
    private String heroNote;
    @Valid
    private FabricSelector fabricSelector;
    @Size(max = 30, message = "Highlights 不能超过 30 项")
    private List<@NotBlank(message = "Highlight 不能为空")
            @Size(max = 500, message = "Highlight 不能超过 500 个字符") String> highlights;
    @Valid
    @Size(max = 20, message = "Option groups 不能超过 20 组")
    private List<OptionGroup> optionGroups;
    @Valid
    @Size(max = 20, message = "Accordions 不能超过 20 组")
    private List<Accordion> accordions;
    @Valid
    @Size(max = 30, message = "Related links 不能超过 30 项")
    private List<RelatedLink> relatedLinks;

    /** Trim public text while retaining the empty-string hide semantic. */
    public void normalize() {
        itemNo = trim(itemNo);
        material = trim(material);
        color = trim(color);
        finish = trim(finish);
        service = trim(service);
        sample = trim(sample);
        packing = trim(packing);
        collection = trim(collection);
        heroNote = trim(heroNote);
    }

    /** Convert the validated DTO to the JSON map used by the existing DO/type handler. */
    public Map<String, Object> toStorageMap() {
        return JsonUtils.parseObject(JsonUtils.toJsonString(this),
                new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Data
    public static class Dimension {

        @Pattern(regexp = "round|rectangular", message = "Dimension shape 只能是 round 或 rectangular")
        private String shape;
        @DecimalMin(value = "0.1", message = "Dimension width 必须大于 0")
        @DecimalMax(value = "1000", message = "Dimension width 不能超过 1000")
        private BigDecimal width;
        @DecimalMin(value = "0.1", message = "Dimension depth 必须大于 0")
        @DecimalMax(value = "1000", message = "Dimension depth 不能超过 1000")
        private BigDecimal depth;
        @DecimalMin(value = "0.1", message = "Dimension diameter 必须大于 0")
        @DecimalMax(value = "1000", message = "Dimension diameter 不能超过 1000")
        private BigDecimal diameter;
        @DecimalMin(value = "0.1", message = "Dimension height 必须大于 0")
        @DecimalMax(value = "1000", message = "Dimension height 不能超过 1000")
        private BigDecimal height;
        @Pattern(regexp = "cm", message = "Dimension unit 只能是 cm")
        private String unit;

        @AssertTrue(message = "Dimension 必须提供完整的形状、尺寸、高度和 cm 单位")
        @JsonIgnore
        public boolean isComplete() {
            if (shape == null && width == null && depth == null && diameter == null
                    && height == null && unit == null) {
                return true;
            }
            if (height == null || !"cm".equals(unit)) {
                return false;
            }
            if ("round".equals(shape)) {
                return diameter != null && width == null && depth == null;
            }
            return "rectangular".equals(shape) && width != null && depth != null && diameter == null;
        }
    }

    @Data
    public static class FabricSelector {

        @Min(value = 0, message = "Stocked count 不能小于 0")
        @Max(value = 10000, message = "Stocked count 不能超过 10000")
        private Integer stockedCount;
        @Min(value = 0, message = "Special-order count 不能小于 0")
        @Max(value = 10000, message = "Special-order count 不能超过 10000")
        private Integer specialOrderCount;
        @Size(max = 255, message = "Selector label 不能超过 255 个字符")
        private String label;
        @Valid
        @Size(max = 100, message = "Swatches 不能超过 100 项")
        private List<Swatch> swatches;
    }

    @Data
    public static class Swatch {

        @NotBlank(message = "Swatch label 不能为空")
        @Size(max = 100, message = "Swatch label 不能超过 100 个字符")
        private String label;
        @NotBlank(message = "Swatch color 不能为空")
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Swatch color 必须是六位十六进制颜色")
        private String swatch;
    }

    @Data
    public static class OptionGroup {

        @NotBlank(message = "Option group key 不能为空")
        @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9-]{0,63}", message = "Option group key 格式不正确")
        private String key;
        @NotBlank(message = "Option group label 不能为空")
        @Size(max = 100, message = "Option group label 不能超过 100 个字符")
        private String label;
        @Size(max = 500, message = "Option group helper 不能超过 500 个字符")
        private String helper;
        @Size(min = 1, max = 100, message = "Option group values 必须包含 1 到 100 项")
        private List<@NotBlank(message = "Option value 不能为空")
                @Size(max = 255, message = "Option value 不能超过 255 个字符") String> values;
    }

    @Data
    public static class Accordion {

        @NotBlank(message = "Accordion title 不能为空")
        @Size(max = 100, message = "Accordion title 不能超过 100 个字符")
        private String title;
        @Size(max = 100, message = "Accordion rows 不能超过 100 行")
        private List<List<@NotBlank(message = "Accordion row value 不能为空")
                @Size(max = 1000, message = "Accordion row value 不能超过 1000 个字符") String>> rows;

        @AssertTrue(message = "Accordion 每行必须恰好包含标题和值")
        @JsonIgnore
        public boolean isRowsValid() {
            return rows == null || rows.stream().allMatch(row -> row != null && row.size() == 2);
        }
    }

    @Data
    public static class RelatedLink {

        @NotBlank(message = "Related-link label 不能为空")
        @Size(max = 255, message = "Related-link label 不能超过 255 个字符")
        private String label;
        @NotBlank(message = "Related-link href 不能为空")
        @Size(max = 1000, message = "Related-link href 不能超过 1000 个字符")
        private String href;
    }

}
