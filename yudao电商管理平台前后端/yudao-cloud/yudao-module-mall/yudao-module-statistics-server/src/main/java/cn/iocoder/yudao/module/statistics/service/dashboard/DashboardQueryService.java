package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;import java.util.List;
public interface DashboardQueryService {DashboardSummaryRespVO summary(DashboardQueryReqVO request,boolean includeProfit);List<DashboardTrendItemRespVO> trend(DashboardQueryReqVO request,boolean includeProfit);List<DashboardProductRespVO> products(DashboardQueryReqVO request,boolean includeProfit);}
