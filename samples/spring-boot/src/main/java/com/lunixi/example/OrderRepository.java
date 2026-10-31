package com.lunixi.example;

import java.util.List;
import java.util.Objects;
import com.lunixi.spring.LunixiProperties;
import com.lunixi.sdk.payment.PaymentDecision;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Same native DataSource as Starter JdbcLedgerStore; no controller holds a transaction across gateway I/O. */
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SampleProperties settings;
    private final LunixiProperties lunixi;
    public OrderRepository(JdbcTemplate jdbc, PlatformTransactionManager manager, SampleProperties settings, LunixiProperties lunixi) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager); this.settings = settings; this.lunixi = lunixi;
    }
    public NativeOrder load(long id) { return select(id, false); }
    public NativeOrder owned(long id, String owner) {
        NativeOrder order = load(id);
        if (owner == null || !owner.equals(order.owner())) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return order;
    }
    public List<NativeOrder> ownedOrders(String owner) {
        return jdbc.query("SELECT * FROM sample_orders WHERE owner_username=? ORDER BY id", (rs, row) -> map(rs), owner);
    }
    public NativeOrder reserve(long id, String owner) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Checkout reservation must commit before gateway I/O."); }
        return transactions.execute(status -> {
            NativeOrder order = select(id, true);
            if (owner == null || !owner.equals(order.owner())) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
            scope(order);
            if (!order.isPayable()) { throw new IllegalStateException("The order is on hold or already settled."); }
            if (order.reservedFingerprint() == null) {
                jdbc.update("UPDATE sample_orders SET reserved_fingerprint=?,reserved_owner=?,reserved_environment=? WHERE id=?", order.snapshot().fingerprint(), order.owner(), order.environment(), id);
            } else { unchanged(order); }
            return order;
        });
    }
    public void apply(long id, PaymentDecision decision, PaidOutbox outbox) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) { throw new IllegalStateException("Native order/inbox must share the Starter transaction."); }
        NativeOrder order = select(id, true); scope(order); unchanged(order);
        if (( !order.paymentId().isEmpty() && !order.paymentId().equals(decision.paymentId())) || "cancelled".equals(order.state())) { throw new IllegalStateException("Native payment identity or cancellation changed."); }
        if (decision.captured() < order.captured() || decision.refunded() < order.refunded()) { throw new IllegalStateException("Native settlement cannot move backwards."); }
        String state = order.manualHold() ? "review" : decision.state();
        jdbc.update("UPDATE sample_orders SET state=?,payment_id=?,charged_amount=?,captured_amount=?,refunded_amount=?,checked_at=? WHERE id=?", state, decision.paymentId(), decision.amount(), decision.captured(), decision.refunded(), System.currentTimeMillis(), id);
        if (decision.isPaid() && !order.manualHold()) { outbox.enqueue(id, decision.paymentId()); }
    }
    public List<Long> reconcileCandidates() {
        return jdbc.queryForList("SELECT id FROM sample_orders WHERE reserved_fingerprint IS NOT NULL AND state NOT IN ('refunded','cancelled') ORDER BY checked_at,id FETCH FIRST 100 ROWS ONLY", Long.class);
    }
    public void checked(long id) { jdbc.update("UPDATE sample_orders SET checked_at=? WHERE id=?", System.currentTimeMillis(), id); }
    private void scope(NativeOrder order) {
        if (!settings.getMerchantId().equals(order.merchant()) || !lunixi.getEnvironment().name().equals(order.environment())) { throw new IllegalStateException("Native merchant/environment scope changed."); }
    }
    private void unchanged(NativeOrder order) {
        if (order.reservedFingerprint() == null || !order.snapshot().fingerprint().equals(order.reservedFingerprint()) || !order.owner().equals(order.reservedOwner()) || !order.environment().equals(order.reservedEnvironment())) { throw new IllegalStateException("Native order changed after payment reservation."); }
    }
    private NativeOrder select(long id, boolean lock) {
        if (id < 1) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        List<NativeOrder> rows = jdbc.query("SELECT * FROM sample_orders WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs, row) -> map(rs), id);
        if (rows.size() != 1) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return rows.get(0);
    }
    private static NativeOrder map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NativeOrder(rs.getLong("id"), rs.getString("owner_username"), rs.getString("merchant_id"), rs.getString("environment"), rs.getLong("base_amount"), rs.getString("currency"), rs.getString("checkout_mode"), rs.getBoolean("preauth"), rs.getString("locale"), rs.getString("state"), rs.getString("payment_id"), rs.getLong("charged_amount"), rs.getLong("captured_amount"), rs.getLong("refunded_amount"), rs.getString("reserved_fingerprint"), rs.getString("reserved_owner"), rs.getString("reserved_environment"), rs.getBoolean("manual_hold"));
    }
}
