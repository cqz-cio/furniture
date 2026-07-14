package cn.iocoder.yudao.module.member.controller.admin.membership.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberMembershipRespVO {

    private Long id;
    private Long userId;
    private String memberId;
    private String planCode;
    private String planName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private Boolean autoRenew;
    private Long sourceOrderId;
    private Long sourcePayOrderId;
    private LocalDateTime createTime;

}
