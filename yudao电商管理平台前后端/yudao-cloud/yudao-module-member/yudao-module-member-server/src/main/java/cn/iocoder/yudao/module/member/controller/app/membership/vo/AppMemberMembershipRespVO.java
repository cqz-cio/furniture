package cn.iocoder.yudao.module.member.controller.app.membership.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppMemberMembershipRespVO {

    private Long id;
    private Long userId;
    private String memberId;
    private String planCode;
    private String planName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private Boolean autoRenew;
    private String accountEmail;
    private String memberEmail;

}
