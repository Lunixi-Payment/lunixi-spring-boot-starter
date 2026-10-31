package com.lunixi.spring;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import javax.sql.DataSource;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.payment.LedgerStore;
import com.lunixi.sdk.payment.OrderSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Native Spring JDBC transactions. The application writes its orders with this same DataSource. */
public final class JdbcLedgerStore implements LedgerStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String prefix;
    private final String dialect;
    private final ThreadLocal<String> activeOrder = new ThreadLocal<>();
    public JdbcLedgerStore(DataSource source, PlatformTransactionManager manager, String prefix) {
        if (!(manager instanceof org.springframework.jdbc.datasource.DataSourceTransactionManager jdbcManager) || jdbcManager.getDataSource() != source) { throw new IllegalArgumentException("The ledger needs the transaction manager for this exact JDBC DataSource."); }
        if (prefix == null || !prefix.matches("[a-zA-Z_][a-zA-Z0-9_]{0,40}")) { throw new IllegalArgumentException("Invalid ledger table prefix."); }
        this.jdbc = new JdbcTemplate(source); this.transactions = new TransactionTemplate(manager); this.prefix = prefix;
        try (Connection connection = source.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            dialect = product.equals("H2") ? "h2" : (product.equals("PostgreSQL") ? "pgsql" : (product.equals("MySQL") || product.equals("MariaDB") ? "mysql" : "unsupported"));
        } catch (SQLException error) { throw new IllegalStateException("Could not inspect the configured ledger database."); }
        if (dialect.equals("unsupported")) { throw new IllegalArgumentException("Use MySQL/InnoDB, PostgreSQL or H2."); }
    }
    /** Explicit migration command only. Never run DDL from a webhook or automatic bean construction. */
    public void install() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Install ledger outside a transaction."); }
        String key = dialect.equals("mysql") ? "VARBINARY(191)" : "VARCHAR(191)";
        String payload = dialect.equals("mysql") ? "LONGTEXT" : "TEXT";
        String suffix = dialect.equals("mysql") ? " ENGINE=InnoDB" : "";
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + prefix + "attempts (order_key " + key + " PRIMARY KEY, payload " + payload + ")" + suffix);
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + prefix + "events (event_hash CHAR(64) PRIMARY KEY, body_hash CHAR(64) NOT NULL, received_at BIGINT NOT NULL)" + suffix);
    }
    @Override public <T> T transaction(String orderKey, Function<RecordAccess, T> operation) {
        if (orderKey == null || orderKey.isBlank() || orderKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 191 || activeOrder.get() != null || TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("A standalone native ledger transaction and valid order key are required."); }
        try {
            activeOrder.set(orderKey);
            return transactions.execute(status -> {
                if (dialect.equals("mysql")) {
                    jdbc.update("INSERT INTO " + prefix + "attempts (order_key,payload) VALUES (?,NULL) ON DUPLICATE KEY UPDATE order_key = VALUES(order_key)", orderKey);
                } else if (dialect.equals("pgsql")) {
                    jdbc.update("INSERT INTO " + prefix + "attempts (order_key,payload) VALUES (?,NULL) ON CONFLICT (order_key) DO NOTHING", orderKey);
                } else {
                    jdbc.update("MERGE INTO " + prefix + "attempts t USING (SELECT CAST(? AS VARCHAR) AS order_key) s ON t.order_key=s.order_key WHEN NOT MATCHED THEN INSERT (order_key,payload) VALUES (s.order_key,NULL)", orderKey);
                }
                jdbc.queryForObject("SELECT order_key FROM " + prefix + "attempts WHERE order_key=? FOR UPDATE", String.class, orderKey);
                return operation.apply(new LockedRecord(orderKey));
            });
        } finally { activeOrder.remove(); }
    }
    private final class LockedRecord implements RecordAccess {
        private final String key;
        private LockedRecord(String key) { this.key = key; }
        private void assertActive() { if (!key.equals(activeOrder.get()) || !TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Ledger access escaped its transaction."); } }
        @Override public Map<String, Object> load() {
            assertActive(); String payload = jdbc.queryForObject("SELECT payload FROM " + prefix + "attempts WHERE order_key=?", String.class, key);
            return payload == null ? null : Json.parseObject(payload);
        }
        @Override public void save(Map<String, Object> record) { assertActive(); jdbc.update("UPDATE " + prefix + "attempts SET payload=? WHERE order_key=?", Json.stringify(record), key); }
        @Override public boolean claimEvent(String merchantId, String eventId, String bodyHash) {
            assertActive();
            if (merchantId == null || merchantId.isBlank() || eventId == null || eventId.isBlank() || bodyHash == null || !bodyHash.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("An authenticated merchant/event identity and body hash are required."); }
            String event = OrderSnapshot.hash(merchantId + '\0' + eventId);
            String insert = dialect.equals("mysql") ? "INSERT IGNORE INTO " : "INSERT INTO ";
            int changed;
            if (dialect.equals("h2")) {
                changed = jdbc.update("MERGE INTO " + prefix + "events t USING (SELECT CAST(? AS VARCHAR) AS event_hash, CAST(? AS VARCHAR) AS body_hash, CAST(? AS BIGINT) AS received_at) s ON t.event_hash=s.event_hash WHEN NOT MATCHED THEN INSERT (event_hash,body_hash,received_at) VALUES (s.event_hash,s.body_hash,s.received_at)", event, bodyHash, Instant.now().getEpochSecond());
            } else {
                changed = jdbc.update(insert + prefix + "events (event_hash,body_hash,received_at) VALUES (?,?,?)" + (dialect.equals("pgsql") ? " ON CONFLICT (event_hash) DO NOTHING" : ""), event, bodyHash, Instant.now().getEpochSecond());
            }
            if (changed == 1) { return true; }
            String previous = jdbc.queryForObject("SELECT body_hash FROM " + prefix + "events WHERE event_hash=?", String.class, event);
            if (!bodyHash.equals(previous)) { throw new IllegalStateException("Authenticated event ID reused with different content."); } return false;
        }
    }
}
