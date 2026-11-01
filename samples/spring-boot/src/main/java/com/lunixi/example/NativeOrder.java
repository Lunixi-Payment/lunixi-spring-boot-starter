package com.lunixi.example;

import java.math.BigDecimal;
import com.lunixi.sdk.payment.Money;
import com.lunixi.sdk.payment.OrderSnapshot;

/** Native authoritative order; principal remains distinct from gross settlement. */
public record NativeOrder(long id, String owner, String merchant, String environment, long amount, String currency,
                          String mode, boolean preauth, String locale, String state, String paymentId,
                          long charged, long captured, long refunded, String reservedFingerprint,
                          String reservedOwner, String reservedEnvironment, boolean manualHold) {
    public OrderSnapshot snapshot() { return new OrderSnapshot(merchant, key(id), Money.encode(amount), currency); }
    public String displayAmount() { return BigDecimal.valueOf(amount, Money.exponent(currency)).toPlainString(); }
    public boolean isPayable() { return "pending".equals(state) && !manualHold; }
    public static String key(long id) {
        if (id < 1) { throw new IllegalArgumentException("A positive native order ID is required."); }
        return "spring:order:" + id;
    }
    @Override public String toString() { return "NativeOrder[id=" + id + ", state=" + state + "]"; }
}
