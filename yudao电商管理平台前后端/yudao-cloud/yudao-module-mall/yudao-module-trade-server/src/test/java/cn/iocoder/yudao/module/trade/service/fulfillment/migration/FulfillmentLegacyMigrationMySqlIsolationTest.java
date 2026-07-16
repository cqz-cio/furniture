package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillmentLegacyMigrationMySqlIsolationTest {

    @Test
    void writerDeclaresReadCommittedToAvoidRepeatableReadPreLockSnapshots() throws Exception {
        Method method = FulfillmentLegacyMigrationWriterImpl.class
                .getMethod("migrateOne", Long.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FULFILLMENT_MYSQL_TEST_URL", matches = ".+")
    void waitingReadCommittedWriterSeesReplayAndResourceCommittedByLockOwner() throws Exception {
        String url = System.getenv("FULFILLMENT_MYSQL_TEST_URL");
        String user = System.getenv().getOrDefault("FULFILLMENT_MYSQL_TEST_USER", "root");
        String password = System.getenv().getOrDefault("FULFILLMENT_MYSQL_TEST_PASSWORD", "test-secret");
        try (Connection setup = DriverManager.getConnection(url, user, password);
             Statement statement = setup.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS migration_mysql_resource");
            statement.execute("DROP TABLE IF EXISTS migration_mysql_idempotency");
            statement.execute("DROP TABLE IF EXISTS migration_mysql_order");
            statement.execute("CREATE TABLE migration_mysql_order (id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE migration_mysql_idempotency "
                    + "(operation_name VARCHAR(64), key_hash VARCHAR(64), resource_id BIGINT, "
                    + "UNIQUE KEY uk_migration_mysql_idem(operation_name, key_hash)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE migration_mysql_resource "
                    + "(id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL) ENGINE=InnoDB");
            statement.execute("INSERT INTO migration_mysql_order VALUES (1)");
        }

        CountDownLatch ownerHasLock = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection owner = DriverManager.getConnection(url, user, password);
             Connection waiter = DriverManager.getConnection(url, user, password)) {
            owner.setAutoCommit(false);
            owner.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            waiter.setAutoCommit(false);
            waiter.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            Future<?> ownerFuture = pool.submit(() -> {
                try (Statement statement = owner.createStatement()) {
                    statement.executeQuery("SELECT * FROM migration_mysql_order WHERE id = 1 FOR UPDATE");
                    ownerHasLock.countDown();
                    assertTrue(waiterStarted.await(10, TimeUnit.SECONDS));
                    statement.executeUpdate("INSERT INTO migration_mysql_resource VALUES (7, 1)");
                    statement.executeUpdate("INSERT INTO migration_mysql_idempotency VALUES "
                            + "('LEGACY_ORDER_MIGRATION', 'safe-key-hash', 7)");
                    owner.commit();
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            Future<Long> waiterFuture = pool.submit(() -> {
                assertTrue(ownerHasLock.await(10, TimeUnit.SECONDS));
                waiterStarted.countDown();
                try (Statement statement = waiter.createStatement()) {
                    statement.executeQuery("SELECT * FROM migration_mysql_order WHERE id = 1 FOR UPDATE");
                    try (ResultSet replay = statement.executeQuery("SELECT resource_id "
                            + "FROM migration_mysql_idempotency WHERE operation_name = "
                            + "'LEGACY_ORDER_MIGRATION' AND key_hash = 'safe-key-hash' FOR UPDATE")) {
                        assertTrue(replay.next());
                        long resourceId = replay.getLong(1);
                        try (ResultSet resource = statement.executeQuery(
                                "SELECT order_id FROM migration_mysql_resource WHERE id = " + resourceId)) {
                            assertTrue(resource.next());
                            return resource.getLong(1);
                        }
                    }
                } finally {
                    waiter.rollback();
                }
            });
            ownerFuture.get(15, TimeUnit.SECONDS);
            assertEquals(1L, waiterFuture.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
