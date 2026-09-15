package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.WebsiteTrafficRespVO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;
@Mapper
public interface WebsiteTrafficEventMapper {
    String FILTER = " FROM statistics_behavior_event WHERE tenant_id=#{tenant} AND event_day >= #{start} AND event_day <= #{end} AND event_type IN (1,5) AND traffic_quality=1 AND deleted=FALSE";
    @Select("SELECT COUNT(*) home_pv, COUNT(DISTINCT visitor_hash) home_uv, MAX(occurred_at) traffic_watermark" + FILTER)
    WebsiteTrafficRespVO summary(@Param("tenant") Long tenant, @Param("start") LocalDate start, @Param("end") LocalDate end);
    @Select("SELECT event_day day, COUNT(*) home_pv, COUNT(DISTINCT visitor_hash) home_uv, MAX(occurred_at) traffic_watermark" + FILTER + " GROUP BY event_day ORDER BY event_day")
    List<WebsiteTrafficRespVO> trend(@Param("tenant") Long tenant, @Param("start") LocalDate start, @Param("end") LocalDate end);
    @Select("SELECT DISTINCT day FROM statistics_behavior_ingestion_gap WHERE tenant_id=#{tenant} AND day >= #{start} AND day <= #{end} AND deleted=FALSE")
    List<LocalDate> gaps(@Param("tenant") Long tenant, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
