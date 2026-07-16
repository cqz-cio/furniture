package cn.iocoder.yudao.module.seo.controller.admin.metadata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.service.metadata.SeoMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - SEO 元数据")
@RestController
@RequestMapping("/seo/metadata")
@Validated
public class SeoMetadataController {

    @Resource
    private SeoMetadataService metadataService;

    @GetMapping("/page")
    @Operation(summary = "获取 SEO 元数据分页")
    @PreAuthorize("@ss.hasPermission('seo:metadata:query')")
    public CommonResult<PageResult<SeoMetadataRespVO>> getMetadataPage(@Valid SeoMetadataPageReqVO reqVO) {
        PageResult<SeoMetadataDO> page = metadataService.getMetadataPage(reqVO);
        return success(BeanUtils.toBean(page, SeoMetadataRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获取 SEO 元数据")
    @PreAuthorize("@ss.hasPermission('seo:metadata:query')")
    public CommonResult<SeoMetadataRespVO> getMetadata(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(metadataService.getMetadata(id), SeoMetadataRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建 SEO 元数据草稿")
    @PreAuthorize("@ss.hasPermission('seo:metadata:create')")
    public CommonResult<Long> createMetadata(@Valid @RequestBody SeoMetadataSaveReqVO reqVO) {
        return success(metadataService.createMetadata(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 SEO 元数据")
    @PreAuthorize("@ss.hasPermission('seo:metadata:update')")
    public CommonResult<Boolean> updateMetadata(@Valid @RequestBody SeoMetadataSaveReqVO reqVO) {
        metadataService.updateMetadata(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 SEO 元数据")
    @PreAuthorize("@ss.hasPermission('seo:metadata:delete')")
    public CommonResult<Boolean> deleteMetadata(@RequestParam("id") Long id) {
        metadataService.deleteMetadata(id);
        return success(true);
    }

    @PutMapping("/publish")
    @Operation(summary = "发布 SEO 元数据")
    @PreAuthorize("@ss.hasPermission('seo:metadata:publish')")
    public CommonResult<Boolean> publishMetadata(@RequestParam("id") Long id,
                                                 @RequestParam("version") Integer version) {
        metadataService.publishMetadata(id, version);
        return success(true);
    }

}
