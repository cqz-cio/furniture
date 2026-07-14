package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.BehaviorEventDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface BehaviorEventMapper extends BaseMapperX<BehaviorEventDO> {
 @org.apache.ibatis.annotations.Delete("DELETE FROM statistics_behavior_event WHERE tenant_id=#{tenantId} AND id IN (SELECT id FROM (SELECT e.id FROM statistics_behavior_event e JOIN statistics_traffic_daily d ON d.tenant_id=e.tenant_id AND d.day=e.event_day AND d.deleted=FALSE WHERE e.tenant_id=#{tenantId} AND e.occurred_at < #{cutoff} AND e.deleted=FALSE AND d.traffic_watermark > e.create_time AND d.traffic_data_status <> 3 ORDER BY e.id LIMIT 10000) purge_ids)")
 int physicalDeleteAggregatedBatch(@org.apache.ibatis.annotations.Param("tenantId")long tenantId,@org.apache.ibatis.annotations.Param("cutoff")java.time.LocalDateTime cutoff);
}
