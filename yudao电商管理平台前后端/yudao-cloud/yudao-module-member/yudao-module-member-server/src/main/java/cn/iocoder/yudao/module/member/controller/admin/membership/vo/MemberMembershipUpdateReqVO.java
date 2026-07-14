package cn.iocoder.yudao.module.member.controller.admin.membership.vo;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class MemberMembershipUpdateReqVO {

    @NotNull(message = "Membership id cannot be empty")
    private Long id;

    @Length(max = 32, message = "Status length cannot exceed 32")
    private String status;

    private LocalDateTime expiresAt;
    private Boolean autoRenew;

}
