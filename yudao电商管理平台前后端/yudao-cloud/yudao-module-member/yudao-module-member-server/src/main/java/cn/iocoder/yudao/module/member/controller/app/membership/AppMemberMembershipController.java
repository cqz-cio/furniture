package cn.iocoder.yudao.module.member.controller.app.membership;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.membership.vo.AppMemberMembershipCheckoutIntentReqVO;
import cn.iocoder.yudao.module.member.controller.app.membership.vo.AppMemberMembershipCheckoutIntentRespVO;
import cn.iocoder.yudao.module.member.controller.app.membership.vo.AppMemberMembershipRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.membership.MemberMembershipDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.membership.MemberMembershipService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "User App - Membership")
@RestController
@RequestMapping("/member/membership")
@Validated
public class AppMemberMembershipController {

    @Resource
    private MemberMembershipService membershipService;
    @Resource
    private MemberUserService memberUserService;

    @GetMapping("/get")
    @Operation(summary = "Get current user's membership")
    public CommonResult<AppMemberMembershipRespVO> getMembership() {
        Long userId = getLoginUserId();
        MemberMembershipDO membership = membershipService.getMembershipByUserId(userId);
        MemberUserDO user = memberUserService.getUser(userId);
        return success(convert(membership, user));
    }

    @PostMapping("/checkout-intent")
    @Operation(summary = "Create annual membership checkout intent")
    public CommonResult<AppMemberMembershipCheckoutIntentRespVO> createCheckoutIntent(
            @RequestBody @Valid AppMemberMembershipCheckoutIntentReqVO reqVO) {
        getLoginUserId();
        AppMemberMembershipCheckoutIntentRespVO respVO = new AppMemberMembershipCheckoutIntentRespVO();
        respVO.setPlanCode(MemberMembershipService.PLAN_ANNUAL);
        respVO.setCheckoutPath("/checkout/auth?intent=membership");
        respVO.setRequiresPayment(true);
        return success(respVO);
    }

    private AppMemberMembershipRespVO convert(MemberMembershipDO membership, MemberUserDO user) {
        AppMemberMembershipRespVO respVO = new AppMemberMembershipRespVO();
        respVO.setUserId(user != null ? user.getId() : null);
        respVO.setAccountEmail(user != null ? user.getEmail() : "");
        respVO.setMemberEmail(user != null ? user.getEmail() : "");
        if (membership == null) {
            respVO.setStatus(MemberMembershipService.STATUS_NOT_MEMBER);
            respVO.setPlanName("None");
            respVO.setAutoRenew(false);
            return respVO;
        }
        respVO.setId(membership.getId());
        respVO.setUserId(membership.getUserId());
        respVO.setMemberId(membership.getMemberId());
        respVO.setPlanCode(membership.getPlanCode());
        respVO.setPlanName(membership.getPlanName());
        respVO.setStatus(membership.getStatus());
        respVO.setStartedAt(membership.getStartedAt());
        respVO.setExpiresAt(membership.getExpiresAt());
        respVO.setAutoRenew(membership.getAutoRenew());
        return respVO;
    }

}
