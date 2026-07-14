package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.BehaviorIngestionGapDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorIngestionGapMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
@Service
public class BehaviorIngestionGapServiceImpl implements BehaviorIngestionGapService {
    @Resource private BehaviorIngestionGapMapper mapper;
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW, rollbackFor=Exception.class)
    public void recordRejected(LocalDateTime now,String reason) {
        LocalDateTime bucket=now.truncatedTo(ChronoUnit.HOURS).plusMinutes((now.getMinute()/5)*5L);
        try { mapper.insert(new BehaviorIngestionGapDO().setDay(now.toLocalDate()).setReasonCode(reason)
                .setBucketStart(bucket).setFirstSeenAt(now).setLastSeenAt(now).setRejectedCount(1L)); }
        catch (DuplicateKeyException duplicate) {
            mapper.update(null,new LambdaUpdateWrapper<BehaviorIngestionGapDO>()
                    .eq(BehaviorIngestionGapDO::getDay,now.toLocalDate()).eq(BehaviorIngestionGapDO::getReasonCode,reason)
                    .eq(BehaviorIngestionGapDO::getBucketStart,bucket)
                    .set(BehaviorIngestionGapDO::getLastSeenAt,now)
                    .setSql("rejected_count = rejected_count + 1"));
        }
    }
}
