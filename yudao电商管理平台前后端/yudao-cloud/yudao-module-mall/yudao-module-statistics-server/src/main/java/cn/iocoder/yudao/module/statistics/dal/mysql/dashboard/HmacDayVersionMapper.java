package cn.iocoder.yudao.module.statistics.dal.mysql.dashboard;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.HmacDayVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
@Mapper public interface HmacDayVersionMapper extends BaseMapperX<HmacDayVersionDO> { default HmacDayVersionDO selectByDay(LocalDate day){ return selectOne(new LambdaQueryWrapper<HmacDayVersionDO>().eq(HmacDayVersionDO::getDay, day)); } }
