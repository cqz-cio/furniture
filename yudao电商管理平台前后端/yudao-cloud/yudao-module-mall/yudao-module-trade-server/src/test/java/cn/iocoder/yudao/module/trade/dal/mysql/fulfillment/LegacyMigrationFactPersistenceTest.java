package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LegacyMigrationFactDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyMigrationFactPersistenceTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 9001L;

    @Resource private LegacyMigrationFactMapper factMapper;
    @Resource private LegacyMigrationReferenceMapper referenceMapper;
    @Resource private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void readsOnlyActiveFactForExactTenantAndHidesApprovalReferenceFromToString() {
        LegacyMigrationFactDO fact = fact("US", "US");
        factMapper.insert(fact);

        LegacyMigrationFactDO selected = factMapper.selectActiveByOrderId(TENANT_ID, ORDER_ID);

        assertEquals(fact.getId(), selected.getId());
        assertEquals("approval-evidence-9001", selected.getSourceReference());
        assertFalse(selected.toString().contains("approval-evidence-9001"));
        assertNull(factMapper.selectActiveByOrderId(999L, ORDER_ID));

        jdbc.update("UPDATE trade_fulfillment_legacy_migration_fact SET deleted=TRUE WHERE id=?", fact.getId());
        assertNull(factMapper.selectActiveByOrderId(TENANT_ID, ORDER_ID));
    }

    @Test
    void absoluteTenantOrderUniquenessStillRejectsReplacementAfterLogicalDelete() {
        LegacyMigrationFactDO first = fact("CA", "CA");
        factMapper.insert(first);
        jdbc.update("UPDATE trade_fulfillment_legacy_migration_fact SET deleted=TRUE WHERE id=?", first.getId());

        assertThrows(DataIntegrityViolationException.class, () -> factMapper.insert(fact("CA", "CA")));
    }

    @Test
    void referenceMapperCountsOnlyEnabledWarehouseInExactTenant() {
        jdbc.update("INSERT INTO erp_warehouse "
                        + "(id, name, status, deleted, tenant_id) VALUES (?,?,?,?,?)",
                501L, "Approved warehouse", 0, false, TENANT_ID);
        jdbc.update("INSERT INTO erp_warehouse "
                        + "(id, name, status, deleted, tenant_id) VALUES (?,?,?,?,?)",
                502L, "Disabled warehouse", 1, false, TENANT_ID);
        jdbc.update("INSERT INTO erp_warehouse "
                        + "(id, name, status, deleted, tenant_id) VALUES (?,?,?,?,?)",
                503L, "Other tenant warehouse", 0, false, 999L);

        assertEquals(1L, referenceMapper.countEnabledWarehouse(TENANT_ID, 501L));
        assertEquals(0L, referenceMapper.countEnabledWarehouse(TENANT_ID, 502L));
        assertEquals(0L, referenceMapper.countEnabledWarehouse(TENANT_ID, 503L));
    }

    private static LegacyMigrationFactDO fact(String originCountry, String destinationCountry) {
        LegacyMigrationFactDO fact = new LegacyMigrationFactDO()
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setOriginCountry(originCountry)
                .setDestinationCountry(destinationCountry)
                .setOriginTimezone("America/New_York")
                .setDestinationTimezone("America/Los_Angeles")
                .setWarehouseId(501L)
                .setMigrationProviderId(601L)
                .setApprovedBy(701L)
                .setApprovedAt(LocalDateTime.of(2026, 7, 16, 9, 30))
                .setSourceReference("approval-evidence-9001");
        fact.setCreator("migration-test");
        fact.setUpdater("migration-test");
        return fact;
    }
}
