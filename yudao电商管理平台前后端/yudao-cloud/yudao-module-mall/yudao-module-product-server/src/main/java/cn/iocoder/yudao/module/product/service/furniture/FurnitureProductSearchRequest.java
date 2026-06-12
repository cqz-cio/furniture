package cn.iocoder.yudao.module.product.service.furniture;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FurnitureProductSearchRequest {

    private String message;
    private String keyword;
    private BigDecimal maxPrice;
    private Integer limit;

}
