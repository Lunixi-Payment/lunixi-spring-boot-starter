package com.lunixi.example;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.spring.JdbcLedgerStore;
import com.lunixi.spring.LunixiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Only an explicit disposable TEST initialization may create this example's schema/accounts. */
@Component
public final class NativeSchema {
    private final JdbcTemplate jdbc;
    private final JdbcLedgerStore ledger;
    private final SampleProperties sample;
    private final LunixiProperties lunixi;
    private final PasswordEncoder passwords;
    public NativeSchema(JdbcTemplate jdbc, JdbcLedgerStore ledger, SampleProperties sample, LunixiProperties lunixi, PasswordEncoder passwords) { this.jdbc = jdbc; this.ledger = ledger; this.sample = sample; this.lunixi = lunixi; this.passwords = passwords; }
    public void initializeDisposable() {
        if (!sample.isInitializeDisposable() || lunixi.getEnvironment() != LunixiConfig.Environment.TEST || TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Explicit disposable TEST initialization is required outside transactions."); }
        if (sample.getAlicePassword().length() < 16 || sample.getBobPassword().length() < 16 || sample.getAlicePassword().equals(sample.getBobPassword())) { throw new IllegalArgumentException("Supply different demo passwords of at least 16 characters; no default login is created."); }
        DataSource source = java.util.Objects.requireNonNull(jdbc.getDataSource());
        try (Connection connection = source.getConnection()) {
            if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) { throw new IllegalArgumentException("This disposable initializer is explicitly H2-only."); }
        } catch (SQLException error) { throw new IllegalStateException("Could not inspect the disposable database."); }
        ledger.install();
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (username VARCHAR(100) PRIMARY KEY,password VARCHAR(200) NOT NULL,enabled BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS authorities (username VARCHAR(100) NOT NULL,authority VARCHAR(100) NOT NULL,CONSTRAINT sample_authority UNIQUE(username,authority),FOREIGN KEY(username) REFERENCES users(username))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sample_orders (id BIGINT PRIMARY KEY,owner_username VARCHAR(100) NOT NULL,merchant_id VARCHAR(191) NOT NULL,environment VARCHAR(4) NOT NULL,base_amount BIGINT NOT NULL CHECK(base_amount>0),currency VARCHAR(3) NOT NULL,checkout_mode VARCHAR(10) NOT NULL,preauth BOOLEAN NOT NULL,locale VARCHAR(2) NOT NULL,state VARCHAR(32) NOT NULL DEFAULT 'pending',payment_id VARCHAR(191) NOT NULL DEFAULT '',charged_amount BIGINT NOT NULL DEFAULT 0,captured_amount BIGINT NOT NULL DEFAULT 0,refunded_amount BIGINT NOT NULL DEFAULT 0,reserved_fingerprint VARCHAR(64),reserved_owner VARCHAR(100),reserved_environment VARCHAR(4),manual_hold BOOLEAN NOT NULL DEFAULT FALSE,checked_at BIGINT NOT NULL DEFAULT 0,FOREIGN KEY(owner_username) REFERENCES users(username))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS sample_outbox (event_key VARCHAR(64) PRIMARY KEY,order_id BIGINT NOT NULL,payload TEXT NOT NULL,state VARCHAR(16) NOT NULL DEFAULT 'ready',FOREIGN KEY(order_id) REFERENCES sample_orders(id))");
        seed("alice", sample.getAlicePassword(), 1L); seed("bob", sample.getBobPassword(), 2L);
    }
    private void seed(String user, String password, long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Integer.class, user) != 0) { return; }
        jdbc.update("INSERT INTO users(username,password,enabled) VALUES (?,?,TRUE)", user, passwords.encode(password));
        jdbc.update("INSERT INTO authorities(username,authority) VALUES (?,'ROLE_CUSTOMER')", user);
        jdbc.update("INSERT INTO sample_orders(id,owner_username,merchant_id,environment,base_amount,currency,checkout_mode,preauth,locale) VALUES (?,?,?,'TEST',1990,'TRY',?,?,?)", id, user, sample.getMerchantId(), sample.getCheckoutMode(), sample.isPreauth(), sample.getOrderLocale());
    }
}
