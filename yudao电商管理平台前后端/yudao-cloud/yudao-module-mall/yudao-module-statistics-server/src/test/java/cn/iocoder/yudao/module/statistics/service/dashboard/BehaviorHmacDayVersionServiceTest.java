package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.HmacDayVersionDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.HmacDayVersionMapper;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BehaviorHmacDayVersionServiceTest {

    @Test
    void registration_usesShanghaiDayBoundaryAndRemainsStable() {
        HmacDayVersionMapper mapper = mock(HmacDayVersionMapper.class);
        when(mapper.selectByDay(any())).thenReturn(null);
        BehaviorTrackingProperties properties = new BehaviorTrackingProperties();
        BehaviorTrackingProperties.TenantHmac tenant = new BehaviorTrackingProperties.TenantHmac();
        tenant.setActiveVersion(3);
        properties.setHmacTenants(Collections.singletonMap("121", tenant));
        BehaviorHmacDayVersionServiceImpl service = new BehaviorHmacDayVersionServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "properties", properties);
        LocalDate day = LocalDate.of(2026, 7, 13);

        assertEquals(3, service.activeVersion(121L, day));

        org.mockito.ArgumentCaptor<HmacDayVersionDO> captor = org.mockito.ArgumentCaptor.forClass(HmacDayVersionDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(day.atStartOfDay(), captor.getValue().getActivatedAt());
        assertEquals(day, captor.getValue().getDay());
    }

    @Test
    void existingRegistrationWinsForWholeTenantDay() {
        HmacDayVersionMapper mapper = mock(HmacDayVersionMapper.class);
        when(mapper.selectByDay(any())).thenReturn(new HmacDayVersionDO().setHashKeyVersion(2));
        BehaviorTrackingProperties properties = new BehaviorTrackingProperties();
        BehaviorTrackingProperties.TenantHmac tenant = new BehaviorTrackingProperties.TenantHmac(); tenant.setActiveVersion(3);
        properties.setHmacTenants(Collections.singletonMap("121", tenant));
        BehaviorHmacDayVersionServiceImpl service = new BehaviorHmacDayVersionServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper); ReflectionTestUtils.setField(service, "properties", properties);
        assertEquals(2, service.activeVersion(121L, LocalDate.now()));
        verify(mapper, never()).insert(any(HmacDayVersionDO.class));
    }
}
