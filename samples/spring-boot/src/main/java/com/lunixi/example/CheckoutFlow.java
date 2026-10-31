package com.lunixi.example;

import java.nio.charset.StandardCharsets;
import com.lunixi.sdk.payment.CheckoutSession;
import com.lunixi.sdk.payment.CommerceService;
import com.lunixi.sdk.webhook.WebhookEvent;
import com.lunixi.spring.LunixiProperties;
import org.springframework.stereotype.Service;

/** Native lifecycle glue; the Starter/SDK exclusively owns payment evidence and replay policy. */
@Service
public final class CheckoutFlow {
    private final OrderRepository orders;
    private final CommerceService commerce;
    private final PaidOutbox outbox;
    private final SampleProperties settings;
    private final LunixiProperties lunixi;
    public CheckoutFlow(OrderRepository orders, CommerceService commerce, PaidOutbox outbox, SampleProperties settings, LunixiProperties lunixi) { this.orders = orders; this.commerce = commerce; this.outbox = outbox; this.settings = settings; this.lunixi = lunixi; }
    public CheckoutSession start(long id, String owner) {
        NativeOrder order = orders.reserve(id, owner);
        return commerce.start(NativeOrder.key(id), order.snapshot(), settings.getPublicBaseUrl() + "/payments/" + id + "/return", "hosted".equals(order.mode()), order.preauth(), order.locale());
    }
    public void webhook(WebhookEvent event, byte[] verifiedRaw) {
        if (!lunixi.getEnvironment().name().equals(event.envelope().get("environment"))) { throw new IllegalStateException("Signed environment does not match the native attempt scope."); }
        Object value = event.data().get("orderId");
        if (!(value instanceof String key) || !key.matches("spring:order:[1-9][0-9]{0,17}")) { throw new IllegalStateException("A signed native order locator is required."); }
        long id = Long.parseLong(key.substring("spring:order:".length()));
        commerce.reconcile(key, event, new String(verifiedRaw, StandardCharsets.UTF_8), decision -> orders.apply(id, decision, outbox));
    }
    public void reconcile(long id) {
        try { commerce.reconcile(NativeOrder.key(id), null, null, decision -> orders.apply(id, decision, outbox)); }
        finally { orders.checked(id); }
    }
}
