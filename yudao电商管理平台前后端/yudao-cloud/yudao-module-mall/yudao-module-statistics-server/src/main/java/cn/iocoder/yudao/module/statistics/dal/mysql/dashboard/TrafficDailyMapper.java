package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.TrafficDailyDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
@Mapper public interface TrafficDailyMapper extends BaseMapperX<TrafficDailyDO> {
 @Delete("DELETE FROM statistics_traffic_daily WHERE tenant_id=#{tenantId} AND day=#{day}") int physicalDeleteByTenantAndDay(@Param("tenantId")long tenantId,@Param("day")LocalDate day);
 default java.util.List<TrafficDailyDO> selectBetween(LocalDate start,LocalDate end){return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrafficDailyDO>().between(TrafficDailyDO::getDay,start,end).orderByAsc(TrafficDailyDO::getDay));}
}
