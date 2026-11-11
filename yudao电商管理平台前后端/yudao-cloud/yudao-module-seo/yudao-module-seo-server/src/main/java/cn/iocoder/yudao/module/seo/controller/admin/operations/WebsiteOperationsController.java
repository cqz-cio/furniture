package cn.iocoder.yudao.module.seo.controller.admin.operations;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.seo.service.operations.WebsiteOperationsService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/seo/operations")
public class WebsiteOperationsController {
    @Resource private WebsiteOperationsService service;
    @GetMapping("/state") @PreAuthorize("@ss.hasPermission('seo:operations:query')")
    public CommonResult<?> state(@RequestParam Long siteId){return success(service.state(siteId));}
    @PostMapping("/file/upload") @PreAuthorize("@ss.hasPermission('seo:operations:update')")
    public CommonResult<?> upload(@RequestParam Long siteId,@RequestParam MultipartFile file){return success(service.upload(siteId,file));}
    @GetMapping("/file/download") @PreAuthorize("@ss.hasPermission('seo:operations:query')")
    public void reviewFile(@RequestParam Long siteId,@RequestParam Long id,jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        byte[] bytes=service.reviewFile(siteId,id);
        response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setContentType("application/pdf");
        response.setHeader("Content-Disposition","attachment; filename=\"review-file-"+id+".pdf\"");response.setContentLength(bytes.length);response.getOutputStream().write(bytes);
    }
    @PostMapping("/file/delete") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> deleteFile(@RequestBody JsonNode body){return success(service.deleteFile(body));}
    @PutMapping("/document") @PreAuthorize("@ss.hasPermission('seo:operations:update')")
    public CommonResult<?> document(@RequestBody JsonNode body){return success(service.saveDocument(body));}
    @PostMapping("/document/publish") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> publish(@RequestBody JsonNode body){return success(service.publishDocument(body));}
    @PostMapping("/document/offline") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> offline(@RequestBody JsonNode body){return success(service.offlineDocument(body));}
    @PostMapping("/document/delete") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> delete(@RequestBody JsonNode body){return success(service.deleteDocument(body));}
    @PostMapping("/review/submit") @PreAuthorize("@ss.hasPermission('seo:operations:update')")
    public CommonResult<?> submit(@RequestBody JsonNode body){return success(service.submitReview(body));}
    @PostMapping("/review/decide") @PreAuthorize("@ss.hasPermission('seo:operations:approve')")
    public CommonResult<?> decide(@RequestBody JsonNode body){return success(service.decideReview(body));}
    @PutMapping("/task") @PreAuthorize("@ss.hasPermission('seo:operations:update')")
    public CommonResult<?> task(@RequestBody JsonNode body){return success(service.saveTask(body));}
    @PostMapping("/task/delete") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> deleteTask(@RequestBody JsonNode body){return success(service.deleteTask(body));}
    @PostMapping("/check") @PreAuthorize("@ss.hasPermission('seo:operations:update')")
    public CommonResult<?> check(@RequestBody JsonNode body){return success(service.check(body.path("siteId").longValue()));}
    @GetMapping("/backup") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> backup(@RequestParam Long siteId){return success(service.backup(siteId));}
    @PostMapping("/backup/check") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> backupCheck(@RequestBody JsonNode body){return success(service.checkBackup(body));}
    @PostMapping("/backup/restore") @PreAuthorize("@ss.hasPermission('seo:operations:publish')")
    public CommonResult<?> restore(@RequestBody JsonNode body){return success(service.restore(body));}
}
