package cn.iocoder.yudao.module.member.controller.admin.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationReviewReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.service.trade.MemberTradeApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Admin - Trade application")
@RestController
@RequestMapping("/member/trade-application")
@Validated
public class MemberTradeApplicationController {

    @Resource
    private MemberTradeApplicationService tradeApplicationService;

    @GetMapping("/page")
    @Operation(summary = "Get Trade application page")
    @PreAuthorize("@ss.hasPermission('member:trade-application:query')")
    public CommonResult<PageResult<MemberTradeApplicationDO>> getTradeApplicationPage(
            @Valid MemberTradeApplicationPageReqVO pageReqVO) {
        return success(tradeApplicationService.getTradeApplicationPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "Get Trade application")
    @Parameter(name = "id", description = "Application id", required = true)
    @PreAuthorize("@ss.hasPermission('member:trade-application:query')")
    public CommonResult<MemberTradeApplicationDO> getTradeApplication(@RequestParam("id") Long id) {
        return success(tradeApplicationService.getTradeApplication(id));
    }

    @PutMapping("/approve")
    @Operation(summary = "Approve Trade application")
    @PreAuthorize("@ss.hasPermission('member:trade-application:review')")
    public CommonResult<Boolean> approveTradeApplication(@RequestBody @Valid MemberTradeApplicationReviewReqVO reqVO) {
        tradeApplicationService.approveTradeApplication(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "Reject Trade application")
    @PreAuthorize("@ss.hasPermission('member:trade-application:review')")
    public CommonResult<Boolean> rejectTradeApplication(@RequestBody @Valid MemberTradeApplicationReviewReqVO reqVO) {
        tradeApplicationService.rejectTradeApplication(reqVO);
        return success(true);
    }

}
