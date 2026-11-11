package cn.iocoder.yudao.module.seo.controller.app.operations;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/seo/operations")
public class AppWebsiteOperationsController {
    @Resource private WebsiteOperationsService service;
    @GetMapping("/documents") @PermitAll
    public CommonResult<?> documents(@RequestParam Long siteId,HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");return success(service.publicDocuments(siteId));}
    @GetMapping("/document/download") @PermitAll
    public void download(@RequestParam Long siteId,@RequestParam Long id,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");
        byte[] bytes;
        try{bytes=service.download(siteId,id);}catch(ServiceException error){response.sendError(404);return;}
        response.setContentType("application/pdf");response.setHeader("Content-Disposition","attachment; filename=\"document-"+id+".pdf\"");response.setContentLength(bytes.length);response.getOutputStream().write(bytes);
    }
}
