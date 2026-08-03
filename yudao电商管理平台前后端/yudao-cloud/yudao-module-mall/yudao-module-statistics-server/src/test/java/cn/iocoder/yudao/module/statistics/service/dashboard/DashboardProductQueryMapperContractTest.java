package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardProductQueryMapperContractTest {

    @Test
    void dashboardProductQueriesAggregateAndPageInsideTheDatabase() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/mapper/product/ProductStatisticsMapper.xml")) {
            assertNotNull(stream);
            String xml = new String(readAll(stream), StandardCharsets.UTF_8);

            assertTrue(xml.contains("<sql id=\"dashboardAggregatedProducts\">"));
            assertTrue(xml.contains("GROUP BY ps.spu_id"));
            assertTrue(xml.contains("<select id=\"countDashboardProducts\""));
            assertTrue(xml.contains("<select id=\"selectDashboardProductPage\""));
            assertTrue(xml.contains("LIMIT #{limit} OFFSET #{offset}"));
            assertTrue(xml.contains("ps.tenant_id = #{tenantId}"));
            assertTrue(xml.contains("spu.tenant_id = ps.tenant_id"));
            assertFalse(xml.contains("${"), "dashboard SQL must not interpolate user-controlled sort or filter values");
        }
    }

    private byte[] readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int length; (length = stream.read(buffer)) != -1; ) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }
}
