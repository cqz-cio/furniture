package cn.iocoder.yudao.module.member.dal.dataobject.membership;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("member_membership")
@KeySequence("member_membership_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberMembershipDO extends TenantBaseDO {

    @TableId
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

}
