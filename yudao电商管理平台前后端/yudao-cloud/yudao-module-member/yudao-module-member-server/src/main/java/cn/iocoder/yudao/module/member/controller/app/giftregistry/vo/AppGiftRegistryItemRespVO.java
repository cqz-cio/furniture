package cn.iocoder.yudao.module.member.controller.app.giftregistry.vo;

import lombok.Data;

@Data
public class AppGiftRegistryItemRespVO {

    private Long id;
    private Long registryId;
    private Long spuId;
    private Long skuId;
    private String productName;
    private String picUrl;
    private Integer price;
    private Integer quantityRequested;
    private Integer quantityPurchased;
    private String priority;
    private String note;

}
