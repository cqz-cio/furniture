package cn.iocoder.yudao.module.member.dal.dataobject.auth;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.member.enums.auth.MemberEmailAuthSceneEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("member_email_auth")
@KeySequence("member_email_auth_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberEmailAuthDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long userId;

    private String email;

    /**
     * 场景，见 {@link MemberEmailAuthSceneEnum}.
     */
    private Integer scene;

    /**
     * 1 token, 2 code.
     */
    private Integer credentialType;

    /**
     * token/code 的 SHA-256 摘要，不保存明文。
     */
    private String credentialHash;

    private LocalDateTime expiresTime;

    private Boolean used;

    private LocalDateTime usedTime;

    private String createIp;

    private String usedIp;

}
