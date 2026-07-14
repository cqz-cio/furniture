package cn.iocoder.yudao.module.product.dal.dataobject.furniture;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@TableName(value = "product_furniture_sku_search", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FurnitureSkuSearchDO extends BaseDO {

    @TableId
    private Long id;
    private Long spuId;
    private Long skuId;
    private String categoryCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> styleCodes;
    private String colorCode;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> materialCodes;
    private Integer seatCount;
    private Integer widthMm;
    private Integer depthMm;
    private Integer heightMm;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> roomTypeCodes;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> featureCodes;
    private Boolean petFriendly;
    private Boolean childFriendly;
    private Boolean easyClean;
    private Boolean scratchResistant;
    private Boolean movable;
    private Boolean rentalFriendly;

}
