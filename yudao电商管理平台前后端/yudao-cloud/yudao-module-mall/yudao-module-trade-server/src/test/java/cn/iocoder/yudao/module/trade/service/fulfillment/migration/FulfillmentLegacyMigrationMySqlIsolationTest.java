package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FulfillmentLegacyMigrationMySqlIsolationTest {

    @Test
    void writerDeclaresReadCommittedToAvoidRepeatableReadPreLockSnapshots() throws Exception {
        Method method = FulfillmentLegacyMigrationWriterImpl.class
                .getMethod("migrateOne", Long.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }
}
