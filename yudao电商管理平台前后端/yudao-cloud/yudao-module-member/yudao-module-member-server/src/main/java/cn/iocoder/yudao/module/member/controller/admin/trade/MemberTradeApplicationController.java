package cn.iocoder.yudao.module.member.controller.admin.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationRespVO;
import cn.iocoder.yudao.module.member.controller.admin.trade.vo.MemberTradeApplicationReviewReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.service.trade.MemberTradeApplicationService;
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
    public CommonResult<PageResult<MemberTradeApplicationRespVO>> getTradeApplicationPage(
            @Valid MemberTradeApplicationPageReqVO pageReqVO) {
        PageResult<MemberTradeApplicationDO> pageResult = tradeApplicationService.getTradeApplicationPage(pageReqVO);
        return success(new PageResult<>(convertList(pageResult.getList(), this::convert), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "Get Trade application")
    @Parameter(name = "id", description = "Application id", required = true)
    @PreAuthorize("@ss.hasPermission('member:trade-application:query')")
    public CommonResult<MemberTradeApplicationRespVO> getTradeApplication(@RequestParam("id") Long id) {
        return success(convert(tradeApplicationService.getTradeApplication(id)));
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

    private MemberTradeApplicationRespVO convert(MemberTradeApplicationDO application) {
        if (application == null) {
            return null;
        }
        MemberTradeApplicationRespVO respVO = new MemberTradeApplicationRespVO();
        respVO.setId(application.getId());
        respVO.setBusinessName(application.getBusinessName());
        respVO.setCountry(application.getCountry());
        respVO.setStreet(application.getStreet());
        respVO.setAddress2(application.getAddress2());
        respVO.setCity(application.getCity());
        respVO.setState(application.getState());
        respVO.setPostalCode(application.getPostalCode());
        respVO.setBusinessDescription(application.getBusinessDescription());
        respVO.setWebsite(application.getWebsite());
        respVO.setPortfolio(application.getPortfolio());
        respVO.setInstagram(application.getInstagram());
        respVO.setPinterest(application.getPinterest());
        respVO.setHouzz(application.getHouzz());
        respVO.setLinkedin(application.getLinkedin());
        respVO.setPrimaryEmail(application.getPrimaryEmail());
        respVO.setAuthorizedUsers(JsonUtils.parseArray(application.getAuthorizedUsersJson(),
                MemberTradeApplicationRespVO.AuthorizedUser.class));
        respVO.setBusinessDocuments(JsonUtils.parseArray(application.getBusinessDocumentsJson(),
                MemberTradeApplicationRespVO.Attachment.class));
        respVO.setTaxDocuments(JsonUtils.parseArray(application.getTaxDocumentsJson(),
                MemberTradeApplicationRespVO.Attachment.class));
        respVO.setEmailOptIn(application.getEmailOptIn());
        respVO.setStatus(application.getStatus());
        respVO.setTradeId(application.getTradeId());
        respVO.setReviewReason(application.getReviewReason());
        respVO.setReviewTime(application.getReviewTime());
        respVO.setReviewerId(application.getReviewerId());
        respVO.setCreateTime(application.getCreateTime());
        return respVO;
    }

}
