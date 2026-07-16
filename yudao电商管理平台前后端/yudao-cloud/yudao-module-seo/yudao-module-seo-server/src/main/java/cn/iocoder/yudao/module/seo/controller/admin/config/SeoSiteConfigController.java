package cn.iocoder.yudao.module.seo.controller.admin.config;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.seo.controller.admin.config.vo.SeoSiteConfigRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.config.vo.SeoSiteConfigSaveReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - SEO 站点配置")
@RestController
@RequestMapping("/seo/site-config")
@Validated
public class SeoSiteConfigController {

    @Resource
    private SeoSiteConfigService siteConfigService;

    @GetMapping("/get")
    @Operation(summary = "获取站点配置")
    @Parameter(name = "siteId", description = "站点编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('seo:site-config:query')")
    public CommonResult<SeoSiteConfigRespVO> getSiteConfig(@RequestParam("siteId") Long siteId) {
        SeoSiteConfigDO config = siteConfigService.getSiteConfig(siteId);
        return success(BeanUtils.toBean(config, SeoSiteConfigRespVO.class));
    }

    @PutMapping("/save")
    @Operation(summary = "保存站点配置")
    @PreAuthorize("@ss.hasPermission('seo:site-config:update')")
    public CommonResult<Boolean> saveSiteConfig(@Valid @RequestBody SeoSiteConfigSaveReqVO reqVO) {
        siteConfigService.saveSiteConfig(reqVO);
        return success(true);
    }

}
