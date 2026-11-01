package com.lunixi.spring;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.payment.Money;
import com.lunixi.sdk.payment.OrderSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Optional explicitly named disposable database only. CI and the recorded local run enable it. */
class MariaDbLedgerTest {
    @Test void realInnoDbSerializesExistingRowsAndRollsBackNativeWritesWithReceipts() throws Exception {
        String file = System.getenv("LUNIXI_TEST_DB_FILE"); assumeTrue(file != null && Files.isRegularFile(Path.of(file)), "Explicit disposable database fixture required.");
        Map<String, Object> config = Json.parseObject(Files.readString(Path.of(file)));
        String database = (String) config.get("database");
        if (database == null || !database.matches("lunixi_[a-zA-Z0-9_]+")) { throw new IllegalArgumentException("Disposable lunixi_ schema required."); }
        String host = (String) config.get("host");
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) { throw new IllegalArgumentException("Local test fixture only."); }
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:mariadb://" + host + ":" + ((Number) config.get("port")).intValue() + "/" + database, (String) config.get("username"), (String) config.get("password"));
        var manager = new DataSourceTransactionManager(source); var jdbc = new JdbcTemplate(source);
        String prefix = "lx_java_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "_";
        JdbcLedgerStore ledger = new JdbcLedgerStore(source, manager, prefix); ledger.install();
        var pool = Executors.newFixedThreadPool(3);
        try {
            jdbc.execute("CREATE TABLE " + prefix + "native_order (id INT PRIMARY KEY, applied INT NOT NULL) ENGINE=InnoDB"); jdbc.update("INSERT INTO " + prefix + "native_order VALUES (1,0)");
            ledger.transaction("existing", record -> { record.save(Map.of("reserved", true)); return null; });
            CountDownLatch start = new CountDownLatch(1); var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 3; i++) { futures.add(pool.submit(() -> { start.await(); return ledger.transaction("existing", record -> {
                if (!record.claimEvent("merchant", "event", OrderSnapshot.hash("raw"))) { return false; } jdbc.update("UPDATE " + prefix + "native_order SET applied=applied+1"); return true;
            }); })); }
            start.countDown(); int applied = 0;
            for (Future<Boolean> future : futures) { if (future.get(20, java.util.concurrent.TimeUnit.SECONDS)) { applied++; } }
            assertEquals(1, applied); assertEquals(1, jdbc.queryForObject("SELECT applied FROM " + prefix + "native_order", Integer.class));
            assertThrows(IllegalStateException.class, () -> ledger.transaction("existing", record -> {
                record.claimEvent("merchant", "rollback-event", OrderSnapshot.hash("rollback")); jdbc.update("UPDATE " + prefix + "native_order SET applied=99"); throw new IllegalStateException("Synthetic native failure");
            }));
            assertEquals(1, jdbc.queryForObject("SELECT applied FROM " + prefix + "native_order", Integer.class));
            boolean retriable = ledger.transaction("existing", record -> record.claimEvent("merchant", "rollback-event", OrderSnapshot.hash("rollback"))); assertTrue(retriable);
            ledger.transaction("Existing", record -> { record.save(Map.of("case", "upper")); return null; });
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM " + prefix + "attempts", Integer.class));
        } finally {
            pool.shutdownNow(); for (String table : new String[]{"events", "attempts", "native_order"}) { jdbc.execute("DROP TABLE IF EXISTS " + prefix + table); }
        }
    }
}
