package cn.iocoder.yudao.module.statistics.controller.admin.dashboard;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardQueryService;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;import javax.annotation.Resource;import javax.validation.Valid;import java.util.List;import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/statistics/dashboard") @Validated
public class DashboardStatisticsController {
 @Resource private DashboardQueryService service;@Resource private SecurityFrameworkService security;
 @GetMapping("/summary") @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')") public CommonResult<DashboardSummaryRespVO> summary(@Valid DashboardQueryReqVO request){return success(service.summary(request,profit()));}
 @GetMapping("/trend") @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')") public CommonResult<List<DashboardTrendItemRespVO>> trend(@Valid DashboardQueryReqVO request){return success(service.trend(request,profit()));}
 @GetMapping("/product-page") @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')") public CommonResult<List<DashboardProductRespVO>> products(@Valid DashboardQueryReqVO request){return success(service.products(request,profit()));}
 private boolean profit(){return security.hasPermission("statistics:dashboard:profit-query");}
}
