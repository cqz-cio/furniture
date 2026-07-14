package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.HmacDayVersionDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.HmacDayVersionMapper;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.time.LocalDate;
@Service
public class BehaviorHmacDayVersionServiceImpl implements BehaviorHmacDayVersionService {
    @Resource private HmacDayVersionMapper mapper;
    @Resource private BehaviorTrackingProperties properties;
    @Override public int activeVersion(Long tenantId, LocalDate day) {
        BehaviorTrackingProperties.TenantHmac config = properties.getHmacTenants().get(String.valueOf(tenantId));
        if (config == null || config.getActiveVersion() == null) throw new IllegalStateException("tenant HMAC is not configured");
        HmacDayVersionDO existing = mapper.selectByDay(day);
        if (existing != null) return existing.getHashKeyVersion();
        HmacDayVersionDO registration = new HmacDayVersionDO().setDay(day)
                .setHashKeyVersion(config.getActiveVersion()).setActivatedAt(day.atStartOfDay());
        try { mapper.insert(registration); }
        catch (DuplicateKeyException ex) {
            existing = mapper.selectByDay(day);
            if (existing == null || !config.getActiveVersion().equals(existing.getHashKeyVersion())) {
                throw new IllegalStateException("tenant/day HMAC version conflict", ex);
            }
        }
        return config.getActiveVersion();
    }
}
