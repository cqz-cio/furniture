package cn.iocoder.yudao.module.member.controller.admin.membership.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "Admin - Membership page request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemberMembershipPageReqVO extends PageParam {

    private Long userId;
    private String memberId;
    private String planCode;
    private String status;

}
