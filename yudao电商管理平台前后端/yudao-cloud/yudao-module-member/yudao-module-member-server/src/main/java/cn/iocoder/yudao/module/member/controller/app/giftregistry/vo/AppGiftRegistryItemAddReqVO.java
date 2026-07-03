package cn.iocoder.yudao.module.member.controller.app.giftregistry.vo;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AppGiftRegistryItemAddReqVO {

    @NotNull
    private Long registryId;
    @NotNull
    private Long spuId;
    @NotNull
    private Long skuId;
    @NotBlank
    private String productName;
    private String picUrl;
    private Integer price;
    @Min(1)
    private Integer quantityRequested;
    private String priority;
    private String note;

}
