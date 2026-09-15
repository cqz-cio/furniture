package cn.iocoder.yudao.module.statistics.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.WebsiteTrafficRespVO;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardQueryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/statistics/website")
@Validated
public class WebsiteTrafficController {
    @Resource private DashboardQueryService service;
    @Resource private cn.iocoder.yudao.module.statistics.service.dashboard.WebsiteTrafficService website;

    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermission('statistics:website:query')")
    public CommonResult<WebsiteTrafficRespVO> summary(@Valid DashboardQueryReqVO request) {
        if (website.configured()) return success(website.summary(request));
        return success(BeanUtils.toBean(service.summary(siteQuery(request), false), WebsiteTrafficRespVO.class));
    }

    @GetMapping("/trend")
    @PreAuthorize("@ss.hasPermission('statistics:website:query')")
    public CommonResult<List<WebsiteTrafficRespVO>> trend(@Valid DashboardQueryReqVO request) {
        if (website.configured()) return success(website.trend(request));
        return success(BeanUtils.toBean(service.trend(siteQuery(request), false), WebsiteTrafficRespVO.class));
    }

    private DashboardQueryReqVO siteQuery(DashboardQueryReqVO request) {
        // Ignore product, financial sorting and comparison parameters entirely.
        DashboardQueryReqVO site = new DashboardQueryReqVO();
        site.setScope("SITE");
        site.setStartDate(request.getStartDate());
        site.setEndDate(request.getEndDate());
        site.setCompare(false);
        return site;
    }
}
