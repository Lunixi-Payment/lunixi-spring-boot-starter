package com.lunixi.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lunixi.sdk.http.HttpResult;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;

/** No network payment: the actual SDK signs/parses against this injected wire fixture. */
final class GatewayFixture implements HttpTransport {
    final List<Call> calls = new ArrayList<>();
    final Map<String, Map<String, Object>> payments = new LinkedHashMap<>();
    final Map<String, String> keys = new LinkedHashMap<>();
    boolean loseCreate;
    String token = "fixture-browser-token";
    Map<String, Object> tamper;
    @Override public synchronized HttpResult send(String method, String url, Map<String, String> headers, String body, Duration timeout) {
        calls.add(new Call(method, url, headers, body));
        if (url.endsWith("/auth/token")) { return result(Map.of("access_token", "fixture-access-token", "expires_in", 3600)); }
        if (method.equals("POST") && url.endsWith("/payments/intents")) {
            String key = headers.get("Idempotency-Key");
            if (key == null) { throw new AssertionError("No durable key on create."); }
            String id = keys.get(key);
            if (id == null) {
                id = "pi-" + (payments.size() + 1); keys.put(key, id);
                Map<String, Object> request = Json.parseObject(body);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id); data.put("merchantId", "merchant-a"); data.put("orderId", request.get("orderId")); data.put("amount", request.get("amount")); data.put("currency", request.get("currency")); data.put("metadata", request.get("metadata")); data.put("status", "INITIATED");
                Map<String, Object> response = new LinkedHashMap<>(); response.put("data", data); response.put("details", Map.of("capturedAmount", "0", "refundedAmount", "0")); payments.put(id, response);
            }
            if (loseCreate) { loseCreate = false; throw new com.lunixi.sdk.exception.LunixiApiException("Synthetic lost create response", new java.io.IOException("fixture")); }
            return result(Map.of("data", Map.of("paymentId", id, "token", token)));
        }
        if (method.equals("GET") && url.contains("/payments/pi-")) {
            String id = url.substring(url.lastIndexOf('/') + 1);
            return result(tamper == null ? payments.get(id) : tamper);
        }
        throw new AssertionError("Unexpected gateway endpoint.");
    }
    void reset() { calls.clear(); payments.clear(); keys.clear(); loseCreate = false; token = "fixture-browser-token"; tamper = null; }
    @SuppressWarnings("unchecked")
    void settle(String state, long amount, long refunded) {
        Map<String, Object> response = payments.get("pi-1");
        ((Map<String, Object>) response.get("data")).put("status", state);
        response.put("details", Map.of("capturedAmount", Long.toString(amount), "refundedAmount", Long.toString(refunded)));
    }
    @SuppressWarnings("unchecked")
    void gross(long amount) { ((Map<String, Object>) payments.get("pi-1").get("data")).put("amount", amount); }
    long creates() { return calls.stream().filter(call -> call.url().endsWith("/payments/intents")).count(); }
    private HttpResult result(Map<String, Object> value) { return new HttpResult(200, Map.of(), Json.stringify(value)); }
    record Call(String method, String url, Map<String, String> headers, String body) {
        @Override public String toString() { return "Call[method=" + method + ", body=[REDACTED], headers=[REDACTED]]"; }
    }
}
