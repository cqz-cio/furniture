package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductRespVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardExportServiceImpl implements DashboardExportService {
    private static final int MAX_ROWS = 10000;

    @Resource
    private DashboardQueryService queryService;

    @Override
    public List<DashboardProductExcelVO> build(DashboardQueryReqVO request, boolean includeProfit) {
        List<DashboardProductRespVO> products = queryService.products(request, includeProfit);
        if (products.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Dashboard export is limited to 10,000 rows; narrow the filters.");
        }
        List<DashboardProductExcelVO> rows = new ArrayList<>(products.size());
        for (DashboardProductRespVO product : products) {
            DashboardProductExcelVO row = new DashboardProductExcelVO()
                    .setSpuId(product.getSpuId()).setBrowseCount(product.getBrowseCount())
                    .setBrowseUserCount(product.getBrowseUserCount()).setCartCount(product.getCartCount())
                    .setOrderCount(product.getOrderCount()).setOrderPayCount(product.getOrderPayCount())
                    .setOrderPayPrice(product.getOrderPayPrice()).setAfterSaleRefundPrice(product.getAfterSaleRefundPrice());
            if (includeProfit) {
                row.setKnownCostAmount(product.getKnownCostAmount()).setCostAmount(product.getCostAmount())
                        .setGrossProfit(product.getGrossProfit()).setGrossMarginPercent(product.getGrossMarginPercent());
            }
            rows.add(row);
        }
        return rows;
    }

    static String escapeFormula(String value) {
        if (value == null || value.isEmpty()) return value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }
}
