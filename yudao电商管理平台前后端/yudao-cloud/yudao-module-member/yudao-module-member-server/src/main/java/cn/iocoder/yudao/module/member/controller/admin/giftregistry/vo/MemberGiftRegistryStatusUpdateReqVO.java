package cn.iocoder.yudao.module.member.controller.admin.giftregistry.vo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class MemberGiftRegistryStatusUpdateReqVO {

    @NotNull
    private Long id;
    @NotBlank
    private String status;

}
