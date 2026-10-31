package com.lunixi.example;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lunixi.sdk.json.Json;
import com.lunixi.sdk.payment.OrderSnapshot;
import com.lunixi.sdk.payment.SecretBox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable domain handoff. A remote worker claims after commit and never blindly retries uncertainty. */
@Service
public final class PaidOutbox {
    private final JdbcTemplate jdbc;
    private final SecretBox box;
    private final TransactionTemplate transactions;
    public PaidOutbox(JdbcTemplate jdbc, SecretBox box, PlatformTransactionManager manager) { this.jdbc = jdbc; this.box = box; this.transactions = new TransactionTemplate(manager); }
    public void enqueue(long orderId, String paymentId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(java.util.Objects.requireNonNull(jdbc.getDataSource()))) { throw new IllegalStateException("Outbox insertion belongs to the same native financial transaction."); }
        String key = OrderSnapshot.hash(paymentId + ":paid");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sample_outbox WHERE event_key=?", Integer.class, key) != 0) { return; }
        Map<String, Object> event = new LinkedHashMap<>(); event.put("type", "order.paid"); event.put("orderId", orderId); event.put("paymentId", paymentId);
        jdbc.update("INSERT INTO sample_outbox(event_key,order_id,payload,state) VALUES (?,?,?,'ready')", key, orderId, box.encrypt(Json.stringify(event)));
    }
    public ClaimedEvent claim() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("External work is forbidden before financial commit."); }
        return transactions.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT event_key,order_id,payload FROM sample_outbox WHERE state='ready' ORDER BY event_key FETCH FIRST 1 ROWS ONLY FOR UPDATE");
            if (rows.isEmpty()) { return null; }
            Map<String, Object> row = rows.get(0);
            String key = (String) row.get("EVENT_KEY");
            long orderId = ((Number) row.get("ORDER_ID")).longValue();
            String state = jdbc.queryForObject("SELECT state FROM sample_orders WHERE id=?", String.class, orderId);
            boolean hold = Boolean.TRUE.equals(jdbc.queryForObject("SELECT manual_hold FROM sample_orders WHERE id=?", Boolean.class, orderId));
            if (!"paid".equals(state) || hold) { jdbc.update("UPDATE sample_outbox SET state='review' WHERE event_key=?", key); return null; }
            jdbc.update("UPDATE sample_outbox SET state='sending' WHERE event_key=?", key);
            return new ClaimedEvent(key, Json.parseObject(box.decrypt((String) row.get("PAYLOAD"))));
        });
    }
    public void acknowledge(String key, boolean confirmed) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Acknowledge external work only after financial commit."); }
        if (key == null || !key.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("Invalid outbox identity."); }
        if (jdbc.update("UPDATE sample_outbox SET state=? WHERE event_key=? AND state='sending'", confirmed ? "sent" : "uncertain", key) != 1) { throw new IllegalStateException("Only a claimed job can be acknowledged."); }
    }
    public record ClaimedEvent(String key, Map<String, Object> event) {
        @Override public String toString() { return "ClaimedEvent[key=" + key + ", payload=[REDACTED]]"; }
    }
}
