package cn.iocoder.yudao.module.member.service.membership;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipOpenReqVO;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.MemberMembershipUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.membership.MemberMembershipDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.membership.MemberMembershipMapper;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBERSHIP_NOT_EXISTS;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_NOT_EXISTS;

@Service
public class MemberMembershipServiceImpl implements MemberMembershipService {

    @Resource
    private MemberMembershipMapper membershipMapper;
    @Resource
    private MemberUserService memberUserService;

    @Override
    public MemberMembershipDO getMembershipByUserId(Long userId) {
        return membershipMapper.selectByUserId(userId);
    }

    @Override
    public MemberMembershipDO getMembership(Long id) {
        return membershipMapper.selectById(id);
    }

    @Override
    public PageResult<MemberMembershipDO> getMembershipPage(MemberMembershipPageReqVO pageReqVO) {
        return membershipMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional
    public MemberMembershipDO openAnnualMembership(MemberMembershipOpenReqVO reqVO) {
        MemberUserDO user = memberUserService.getUser(reqVO.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        MemberMembershipDO membership = membershipMapper.selectByUserId(reqVO.getUserId());
        if (membership == null) {
            membership = MemberMembershipDO.builder()
                    .userId(reqVO.getUserId())
                    .memberId(buildMemberId(reqVO.getUserId()))
                    .planCode(PLAN_ANNUAL)
                    .planName(PLAN_ANNUAL_NAME)
                    .status(STATUS_ACTIVE_ANNUAL)
                    .startedAt(now)
                    .expiresAt(now.plusYears(1))
                    .autoRenew(true)
                    .sourceOrderId(reqVO.getSourceOrderId())
                    .sourcePayOrderId(reqVO.getSourcePayOrderId())
                    .build();
            membershipMapper.insert(membership);
            return membership;
        }

        membershipMapper.updateById(new MemberMembershipDO()
                .setId(membership.getId())
                .setMemberId(StrUtil.blankToDefault(membership.getMemberId(), buildMemberId(reqVO.getUserId())))
                .setPlanCode(PLAN_ANNUAL)
                .setPlanName(PLAN_ANNUAL_NAME)
                .setStatus(STATUS_ACTIVE_ANNUAL)
                .setStartedAt(membership.getStartedAt() == null ? now : membership.getStartedAt())
                .setExpiresAt(now.plusYears(1))
                .setAutoRenew(true)
                .setSourceOrderId(reqVO.getSourceOrderId())
                .setSourcePayOrderId(reqVO.getSourcePayOrderId()));
        return membershipMapper.selectById(membership.getId());
    }

    @Override
    @Transactional
    public void updateMembership(MemberMembershipUpdateReqVO reqVO) {
        MemberMembershipDO membership = membershipMapper.selectById(reqVO.getId());
        if (membership == null) {
            throw exception(MEMBERSHIP_NOT_EXISTS);
        }
        membershipMapper.updateById(new MemberMembershipDO()
                .setId(reqVO.getId())
                .setStatus(reqVO.getStatus())
                .setExpiresAt(reqVO.getExpiresAt())
                .setAutoRenew(reqVO.getAutoRenew()));
    }

    private String buildMemberId(Long userId) {
        return "OAKVED-MEMBER-" + userId;
    }

}
