package cn.iocoder.yudao.module.seo.controller.admin.page;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.WebsitePreviewReqVO;
import cn.iocoder.yudao.module.seo.service.page.WebsitePreviewService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/seo/site-preview")
public class WebsitePreviewController {
    @Resource private WebsitePreviewService service;
    @PostMapping("/ticket")
    @PreAuthorize("@ss.hasPermission('seo:page:preview') and @ss.hasPermission('seo:navigation:preview') and @ss.hasPermission('seo:blog:preview')")
    public CommonResult<Map<String, Object>> create(@Valid @RequestBody WebsitePreviewReqVO request) {
        return CommonResult.success(service.create(request));
    }
}
