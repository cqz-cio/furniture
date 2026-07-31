package cn.iocoder.yudao.module.system.controller.admin.inquiry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigRespVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailConfigSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.inquiry.vo.WebsiteInquiryMailDeliveryRespVO;
import cn.iocoder.yudao.module.system.service.inquiry.mail.WebsiteInquiryMailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 官网询盘邮件转发")
@RestController
@RequestMapping("/system/website-inquiry-mail")
public class WebsiteInquiryMailController {

    @Resource
    private WebsiteInquiryMailService websiteInquiryMailService;

    @GetMapping("/config")
    @Operation(summary = "获得当前租户的询盘邮件配置")
    @PreAuthorize("@ss.hasPermission('crm:clue:query')")
    public CommonResult<WebsiteInquiryMailConfigRespVO> getConfig() {
        return success(websiteInquiryMailService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "保存当前租户的询盘邮件配置")
    @PreAuthorize("@ss.hasPermission('crm:clue:update')")
    public CommonResult<Boolean> saveConfig(
            @Valid @RequestBody WebsiteInquiryMailConfigSaveReqVO reqVO) {
        websiteInquiryMailService.saveConfig(reqVO);
        return success(true);
    }

    @PostMapping("/test")
    @Operation(summary = "向绑定邮箱发送测试询盘邮件")
    @PreAuthorize("@ss.hasPermission('crm:clue:update')")
    public CommonResult<Long> sendTestMail() {
        return success(websiteInquiryMailService.sendTestMail());
    }

    @GetMapping("/delivery")
    @Operation(summary = "获得指定询盘的邮件投递状态")
    @Parameter(name = "inquiryId", description = "CRM 询盘编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:clue:query')")
    public CommonResult<WebsiteInquiryMailDeliveryRespVO> getDelivery(
            @RequestParam("inquiryId") Long inquiryId) {
        return success(websiteInquiryMailService.getDelivery(inquiryId));
    }

    @PostMapping("/resend")
    @Operation(summary = "手动重发指定询盘邮件")
    @Parameter(name = "inquiryId", description = "CRM 询盘编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:clue:update')")
    public CommonResult<Boolean> resend(@RequestParam("inquiryId") Long inquiryId) {
        websiteInquiryMailService.resend(inquiryId);
        return success(true);
    }

}
