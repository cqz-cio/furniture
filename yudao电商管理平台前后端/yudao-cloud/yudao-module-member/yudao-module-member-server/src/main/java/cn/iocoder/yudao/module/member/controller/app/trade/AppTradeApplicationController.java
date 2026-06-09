package cn.iocoder.yudao.module.member.controller.app.trade;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.trade.vo.AppTradeApplicationSubmitReqVO;
import cn.iocoder.yudao.module.member.controller.app.trade.vo.AppTradeApplicationSubmitRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.trade.MemberTradeApplicationDO;
import cn.iocoder.yudao.module.member.service.trade.MemberTradeApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "User App - Trade application")
@RestController
@RequestMapping("/member/auth")
@Validated
public class AppTradeApplicationController {

    @Resource
    private MemberTradeApplicationService tradeApplicationService;

    @PostMapping("/trade-application")
    @Operation(summary = "Submit Trade Program application")
    @PermitAll
    public CommonResult<AppTradeApplicationSubmitRespVO> submitTradeApplication(
            @RequestBody @Valid AppTradeApplicationSubmitReqVO reqVO) {
        MemberTradeApplicationDO application = tradeApplicationService.submitTradeApplication(reqVO);
        return success(new AppTradeApplicationSubmitRespVO(application.getId(), application.getStatus()));
    }

}
