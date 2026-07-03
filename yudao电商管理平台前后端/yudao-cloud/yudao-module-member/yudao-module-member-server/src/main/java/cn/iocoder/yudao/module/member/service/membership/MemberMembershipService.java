package cn.iocoder.yudao.module.member.service.membership;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipOpenReqVO;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.membership.MemberMembershipDO;

import javax.validation.Valid;

public interface MemberMembershipService {

    String STATUS_NOT_MEMBER = "not_member";
    String STATUS_ACTIVE_ANNUAL = "active_annual";
    String STATUS_EXPIRED = "expired";
    String PLAN_ANNUAL = "annual_membership";
    String PLAN_ANNUAL_NAME = "Annual Membership";

    MemberMembershipDO getMembershipByUserId(Long userId);

    MemberMembershipDO getMembership(Long id);

    PageResult<MemberMembershipDO> getMembershipPage(MemberMembershipPageReqVO pageReqVO);

    MemberMembershipDO openAnnualMembership(@Valid MemberMembershipOpenReqVO reqVO);

    void updateMembership(@Valid MemberMembershipUpdateReqVO reqVO);

}
