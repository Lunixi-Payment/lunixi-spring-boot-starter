package com.lunixi.example;

import java.math.BigDecimal;
import com.lunixi.sdk.payment.Money;
import com.lunixi.sdk.payment.OrderSnapshot;
import com.lunixi.sdk.json.Json;
import java.util.List;
import java.util.Map;

/** Native authoritative order; principal remains distinct from gross settlement. */
public record NativeOrder(long id, String owner, String merchant, String environment, long amount, String currency,
                          String mode, boolean preauth, String locale, String state, String paymentId,
                          long charged, long captured, long refunded, String reservedFingerprint,
                          String reservedOwner, String reservedEnvironment, boolean manualHold, String orderDetails) {
    /** The order with its stored order details; CommerceService.start refuses an incomplete one. */
    @SuppressWarnings("unchecked")
    public OrderSnapshot snapshot() {
        Map<String, Object> details = Json.parseObject(orderDetails == null ? "{}" : orderDetails);
        return new OrderSnapshot(merchant, key(id), Money.encode(amount), currency).withOrderDetails(
                (Map<String, Object>) details.get("buyer"), (Map<String, Object>) details.get("billingAddress"),
                (Map<String, Object>) details.get("shippingAddress"),
                details.get("basketItems") == null ? List.of() : (List<Map<String, Object>>) details.get("basketItems"));
    }
    public String displayAmount() { return BigDecimal.valueOf(amount, Money.exponent(currency)).toPlainString(); }
    public boolean isPayable() { return "pending".equals(state) && !manualHold; }
    public static String key(long id) {
        if (id < 1) { throw new IllegalArgumentException("A positive native order ID is required."); }
        return "spring:order:" + id;
    }
    @Override public String toString() { return "NativeOrder[id=" + id + ", state=" + state + "]"; }
}
