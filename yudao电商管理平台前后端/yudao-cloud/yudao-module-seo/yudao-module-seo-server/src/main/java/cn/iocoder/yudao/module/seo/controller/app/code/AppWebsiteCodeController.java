package cn.iocoder.yudao.module.seo.controller.app.code;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.code.vo.WebsiteCodeRespVO;
import cn.iocoder.yudao.module.seo.service.code.WebsiteCodeService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/seo/website-code")
@Validated
public class AppWebsiteCodeController {
    @Resource private WebsiteCodeService service;

    @GetMapping("/public")
    @PermitAll
    public CommonResult<WebsiteCodeRespVO> published(@RequestParam("siteId") @Min(1) Long siteId,
                                                    HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Vary", "tenant-id");
        return success(service.getPublished(siteId));
    }
}
