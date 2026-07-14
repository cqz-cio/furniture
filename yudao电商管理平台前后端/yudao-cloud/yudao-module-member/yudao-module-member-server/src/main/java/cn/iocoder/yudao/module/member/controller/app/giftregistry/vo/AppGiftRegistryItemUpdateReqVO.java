package cn.iocoder.yudao.module.member.controller.app.giftregistry.vo;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class AppGiftRegistryItemUpdateReqVO {

    @NotNull
    private Long id;
    @Min(1)
    private Integer quantityRequested;
    private String priority;
    private String note;

}
