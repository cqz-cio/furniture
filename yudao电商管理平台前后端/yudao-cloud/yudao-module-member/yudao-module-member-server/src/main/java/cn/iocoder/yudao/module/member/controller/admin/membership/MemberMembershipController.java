package cn.iocoder.yudao.module.member.controller.admin.membership;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.membership.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.membership.MemberMembershipDO;
import cn.iocoder.yudao.module.member.service.membership.MemberMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "Admin - Membership")
@RestController
@RequestMapping("/member/membership")
@Validated
public class MemberMembershipController {

    @Resource
    private MemberMembershipService membershipService;

    @GetMapping("/page")
    @Operation(summary = "Get membership page")
    @PreAuthorize("@ss.hasPermission('member:membership:query')")
    public CommonResult<PageResult<MemberMembershipRespVO>> getMembershipPage(
            @Valid MemberMembershipPageReqVO pageReqVO) {
        PageResult<MemberMembershipDO> pageResult = membershipService.getMembershipPage(pageReqVO);
        return success(new PageResult<>(convertList(pageResult.getList(), this::convert), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "Get membership")
    @Parameter(name = "id", description = "Membership id", required = true)
    @PreAuthorize("@ss.hasPermission('member:membership:query')")
    public CommonResult<MemberMembershipRespVO> getMembership(@RequestParam("id") Long id) {
        return success(convert(membershipService.getMembership(id)));
    }

    @PostMapping("/open")
    @Operation(summary = "Open annual membership for a user")
    @PreAuthorize("@ss.hasPermission('member:membership:update')")
    public CommonResult<MemberMembershipRespVO> openMembership(@RequestBody @Valid MemberMembershipOpenReqVO reqVO) {
        return success(convert(membershipService.openAnnualMembership(reqVO)));
    }

    @PutMapping("/update")
    @Operation(summary = "Update membership status")
    @PreAuthorize("@ss.hasPermission('member:membership:update')")
    public CommonResult<Boolean> updateMembership(@RequestBody @Valid MemberMembershipUpdateReqVO reqVO) {
        membershipService.updateMembership(reqVO);
        return success(true);
    }

    private MemberMembershipRespVO convert(MemberMembershipDO membership) {
        if (membership == null) {
            return null;
        }
        MemberMembershipRespVO respVO = new MemberMembershipRespVO();
        respVO.setId(membership.getId());
        respVO.setUserId(membership.getUserId());
        respVO.setMemberId(membership.getMemberId());
        respVO.setPlanCode(membership.getPlanCode());
        respVO.setPlanName(membership.getPlanName());
        respVO.setStatus(membership.getStatus());
        respVO.setStartedAt(membership.getStartedAt());
        respVO.setExpiresAt(membership.getExpiresAt());
        respVO.setAutoRenew(membership.getAutoRenew());
        respVO.setSourceOrderId(membership.getSourceOrderId());
        respVO.setSourcePayOrderId(membership.getSourcePayOrderId());
        respVO.setCreateTime(membership.getCreateTime());
        return respVO;
    }

}
