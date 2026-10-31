package com.lunixi.spring;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.lunixi.sdk.payment.OrderSnapshot;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcLedgerTest {
    JdbcLedgerStore store;
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        manager = new DataSourceTransactionManager(source); store = new JdbcLedgerStore(source, manager, "lx_"); store.install(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE native_order (id INT PRIMARY KEY, applied INT NOT NULL)"); jdbc.update("INSERT INTO native_order VALUES (1,0)");
    }
    @Test void nativeOrderLedgerAndInboxRollbackTogether() {
        assertThrows(IllegalStateException.class, () -> store.transaction("order", record -> {
            record.save(Map.of("paid", true)); record.claimEvent("merchant", "event", OrderSnapshot.hash("raw")); jdbc.update("UPDATE native_order SET applied=1"); throw new IllegalStateException("Synthetic native persistence failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT applied FROM native_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lx_events", Integer.class));
        boolean first = store.transaction("order", record -> record.claimEvent("merchant", "event", OrderSnapshot.hash("raw")));
        boolean duplicate = store.transaction("order", record -> record.claimEvent("merchant", "event", OrderSnapshot.hash("raw")));
        assertTrue(first); assertFalse(duplicate);
        assertThrows(IllegalStateException.class, () -> store.transaction("order", record -> record.claimEvent("merchant", "event", OrderSnapshot.hash("different"))));
    }
    @Test void concurrentConsumersOfAnExistingOrderApplyOnce() throws Exception {
        store.transaction("order", record -> { record.save(Map.of("reserved", true)); return null; });
        var pool = Executors.newFixedThreadPool(3); CountDownLatch start = new CountDownLatch(1); var futures = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < 3; i++) { futures.add(pool.submit(() -> { start.await(); return store.transaction("order", record -> { if (!record.claimEvent("merchant", "event", OrderSnapshot.hash("raw"))) { return false; } jdbc.update("UPDATE native_order SET applied=applied+1"); return true; }); })); }
            start.countDown(); int applied = 0;
            for (Future<Boolean> future : futures) { if (future.get(15, java.util.concurrent.TimeUnit.SECONDS)) { applied++; } }
            assertEquals(1, applied); assertEquals(1, jdbc.queryForObject("SELECT applied FROM native_order", Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void rejectsAmbientTransactionsAndEscapedLedgerAccess() {
        new TransactionTemplate(manager).execute(status -> { assertThrows(IllegalStateException.class, () -> store.transaction("order", record -> null)); return null; });
        var access = store.transaction("order", record -> record);
        assertThrows(IllegalStateException.class, access::load);
        assertThrows(IllegalArgumentException.class, () -> new JdbcLedgerStore(jdbc.getDataSource(), manager, "unsafe;DROP"));
    }
    @Test void orderKeysRemainCaseSensitive() {
        store.transaction("Order-A", record -> { record.save(Map.of("name", "upper")); return null; });
        store.transaction("order-a", record -> { record.save(Map.of("name", "lower")); return null; });
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM lx_attempts", Integer.class));
    }
}
