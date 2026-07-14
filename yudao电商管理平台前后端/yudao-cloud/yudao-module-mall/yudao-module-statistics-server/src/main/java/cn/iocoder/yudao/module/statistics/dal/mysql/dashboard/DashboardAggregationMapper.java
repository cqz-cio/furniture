package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.TrafficDailyDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.product.ProductStatisticsDO;
import org.apache.ibatis.annotations.*;
import java.time.*;
import java.util.List;
@Mapper public interface DashboardAggregationMapper {
 TrafficDailyDO selectTrafficDaily(@Param("tenantId")long tenantId,@Param("day")LocalDate day,@Param("begin")LocalDateTime begin,@Param("end")LocalDateTime end);
 List<ProductStatisticsDO> selectProductDaily(@Param("tenantId")long tenantId,@Param("day")LocalDate day,@Param("begin")LocalDateTime begin,@Param("end")LocalDateTime end);
 Integer countHashVersions(@Param("tenantId")long tenantId,@Param("day")LocalDate day);
}
