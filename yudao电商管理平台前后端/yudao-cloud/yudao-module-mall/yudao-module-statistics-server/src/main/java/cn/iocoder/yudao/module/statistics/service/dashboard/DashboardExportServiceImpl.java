package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductNormalExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductRespVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.converters.longconverter.LongStringConverter;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.hutool.crypto.SecureUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;

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
                    .setSpuId(product.getSpuId()).setProductName(escapeFormula(product.getProductName()))
                    .setCategoryId(product.getCategoryId()).setBrowseCount(product.getBrowseCount())
                    .setBrowseUserCount(product.getBrowseUserCount()).setCartCount(product.getCartCount())
                    .setOrderCount(product.getOrderCount()).setOrderPayCount(product.getOrderPayCount())
                    .setOrderPayPrice(product.getOrderPayPrice()).setAfterSaleCount(product.getAfterSaleCount())
                    .setAfterSaleRefundPrice(product.getAfterSaleRefundPrice()).setNetRevenue(product.getNetRevenue())
                    .setBrowseConvertPercent(product.getBrowseConvertPercent()).setTrafficDataStatus(product.getTrafficDataStatus());
            if (includeProfit) {
                row.setKnownCostAmount(product.getKnownCostAmount()).setCostAmount(product.getCostAmount())
                        .setGrossProfit(product.getGrossProfit()).setGrossMarginPercent(product.getGrossMarginPercent())
                        .setMissingCostItemCount(product.getMissingCostItemCount()).setProfitDataQuality(product.getProfitDataQuality());
            }
            rows.add(row);
        }
        return rows;
    }

    @Override
    public DashboardExportArtifact generate(DashboardQueryReqVO request, boolean includeProfit) {
        List<DashboardProductExcelVO> rows = build(request, includeProfit);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (includeProfit) {
            FastExcelFactory.write(output, DashboardProductExcelVO.class)
                    .autoCloseStream(false).registerConverter(new LongStringConverter())
                    .sheet("数据").doWrite(rows);
        } else {
            FastExcelFactory.write(output, DashboardProductNormalExcelVO.class)
                    .autoCloseStream(false).registerConverter(new LongStringConverter())
                    .sheet("数据").doWrite(BeanUtils.toBean(rows, DashboardProductNormalExcelVO.class));
        }
        byte[] content = output.toByteArray();
        return new DashboardExportArtifact(content, SecureUtil.sha256().digestHex(content), rows.size(), rows);
    }

    static String escapeFormula(String value) {
        if (value == null || value.isEmpty()) return value;
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }
}
