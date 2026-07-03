package cn.iocoder.yudao.module.member.controller.admin.membership.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class MemberMembershipOpenReqVO {

    @NotNull(message = "User id cannot be empty")
    private Long userId;
    private Long sourceOrderId;
    private Long sourcePayOrderId;

}
