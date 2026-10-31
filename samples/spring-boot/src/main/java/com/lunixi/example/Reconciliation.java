package com.lunixi.example;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sample.reconciliation-enabled", havingValue = "true")
public class Reconciliation {
    private final OrderRepository orders;
    private final CheckoutFlow flow;
    public Reconciliation(OrderRepository orders, CheckoutFlow flow) { this.orders = orders; this.flow = flow; }
    @Scheduled(initialDelay = 60000, fixedDelayString = "${sample.reconciliation-delay:60000}")
    public void reconcile() {
        for (long id : orders.reconcileCandidates()) { try { flow.reconcile(id); } catch (RuntimeException needsReview) { /* Native state remains on hold; do not log credentials, buyer data or gateway bodies. */ } }
    }
}
