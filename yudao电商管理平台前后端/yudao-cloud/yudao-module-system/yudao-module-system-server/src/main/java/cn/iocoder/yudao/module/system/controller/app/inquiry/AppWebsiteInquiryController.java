package cn.iocoder.yudao.module.system.controller.app.inquiry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySubmitReqVO;
import cn.iocoder.yudao.module.system.service.inquiry.WebsiteInquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 官网询盘")
@RestController
@RequestMapping("/system/website-inquiry")
@Validated
public class AppWebsiteInquiryController {

    public static final String HEADER_SHARED_SECRET = "X-Vanz-Inquiry-Secret";

    @Resource
    private WebsiteInquiryService websiteInquiryService;

    @PostMapping("/notify")
    @PermitAll
    @Operation(summary = "将官网询盘发送为 ERP 站内信")
    public CommonResult<Long> notifyInquiry(
            @RequestHeader(value = HEADER_SHARED_SECRET, required = false) String sharedSecret,
            @Valid @RequestBody AppWebsiteInquirySubmitReqVO reqVO) {
        return success(websiteInquiryService.notifyInquiry(sharedSecret, reqVO));
    }

}
