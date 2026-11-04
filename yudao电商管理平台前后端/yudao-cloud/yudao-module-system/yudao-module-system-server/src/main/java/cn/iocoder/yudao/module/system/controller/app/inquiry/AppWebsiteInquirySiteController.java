package cn.iocoder.yudao.module.system.controller.app.inquiry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.app.inquiry.vo.AppWebsiteInquirySiteSubmitReqVO;
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

/**
 * 用户 App - 官网询盘（独立站点隔离端点）。
 *
 * <p>与旧 {@link AppWebsiteInquiryController} 的 {@code /notify}（VANZ 162 锁）互不影响。
 * 本端点通过 {@code X-Site-Inquiry-Site} 站点键解析站点配置，用 {@code X-Site-Inquiry-Secret}
 * 做恒定时间密钥比较；租户上下文来自框架 {@code tenant-id} 请求头，浏览器无法选择站点或租户。</p>
 */
@Tag(name = "用户 App - 官网询盘（站点隔离）")
@RestController
@RequestMapping("/system/website-inquiry")
@Validated
public class AppWebsiteInquirySiteController {

    public static final String HEADER_SITE = "X-Site-Inquiry-Site";
    public static final String HEADER_SECRET = "X-Site-Inquiry-Secret";

    @Resource
    private WebsiteInquiryService websiteInquiryService;

    @PostMapping("/site-notify")
    @PermitAll
    @Operation(summary = "将站点官网询盘写入 ERP 询盘中心（独立站点隔离）")
    public CommonResult<Long> siteNotifyInquiry(
            @RequestHeader(value = HEADER_SITE, required = false) String siteKey,
            @RequestHeader(value = HEADER_SECRET, required = false) String sharedSecret,
            @Valid @RequestBody AppWebsiteInquirySiteSubmitReqVO reqVO) {
        return success(websiteInquiryService.siteNotifyInquiry(siteKey, sharedSecret, reqVO));
    }

}
