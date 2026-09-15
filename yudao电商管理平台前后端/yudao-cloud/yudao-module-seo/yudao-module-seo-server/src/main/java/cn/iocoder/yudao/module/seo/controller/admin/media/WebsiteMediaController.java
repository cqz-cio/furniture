package cn.iocoder.yudao.module.seo.controller.admin.media;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.seo.controller.admin.media.vo.*;
import cn.iocoder.yudao.module.seo.service.media.WebsiteMediaService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/seo/media") @Validated
public class WebsiteMediaController {
    @Resource private WebsiteMediaService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('seo:media:query')")
    public CommonResult<PageResult<WebsiteMediaRespVO>> page(@Valid WebsiteMediaPageReqVO request) {
        return success(BeanUtils.toBean(service.page(request), WebsiteMediaRespVO.class));
    }
    @PostMapping("/upload") @PreAuthorize("@ss.hasPermission('seo:media:upload')")
    public CommonResult<WebsiteMediaRespVO> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value="alt", defaultValue="") String alt) {
        return success(BeanUtils.toBean(service.upload(file, alt), WebsiteMediaRespVO.class));
    }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('seo:media:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody WebsiteMediaUpdateReqVO request) {
        service.update(request); return success(true);
    }
    @PutMapping("/archive") @PreAuthorize("@ss.hasPermission('seo:media:archive')")
    public CommonResult<Boolean> archive(@RequestParam("id") @Positive Long id, @RequestParam("archived") boolean archived) {
        service.archive(id, archived); return success(true);
    }
}
