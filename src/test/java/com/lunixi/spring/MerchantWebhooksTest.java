package com.lunixi.spring;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import com.lunixi.sdk.webhook.WebhookVerifier;

class MerchantWebhooksTest {
    private Map<String, List<String>> headers(String body, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("fixture-only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Map.of("x-lunixi-event-id", List.of("event"), "x-lunixi-signature-timestamp", List.of(timestamp), "x-lunixi-signature", List.of("v2=" + HexFormat.of().formatHex(mac.doFinal(("event." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)))));
    }
    @Test void requiresExactSignedEnvelopeAndRawBytes() throws Exception {
        String body = "{\"id\":\"event\",\"type\":\"payment.captured\",\"data\":{\"intentId\":\"payment\"}}";
        var receiver = new MerchantWebhooks(new WebhookVerifier(), "fixture-only"); var headers = headers(body, Instant.now().toString());
        assertEquals("event", receiver.verify(body.getBytes(StandardCharsets.UTF_8), headers).id());
        assertThrows(RuntimeException.class, () -> receiver.verify((body + " ").getBytes(StandardCharsets.UTF_8), headers));
        assertThrows(RuntimeException.class, () -> receiver.verify(body.getBytes(StandardCharsets.UTF_8), headers(body, Instant.now().minusSeconds(600).toString())));
        assertThrows(RuntimeException.class, () -> receiver.verify(new byte[]{(byte) 0xff}, headers));
        assertThrows(RuntimeException.class, () -> receiver.verify(new byte[1_048_577], headers));
    }
    @Test void rejectsDuplicateHeadersAndUnsignedTypeFallback() throws Exception {
        String body = "{\"id\":\"event\",\"data\":{}}"; String ts = Instant.now().toString();
        var receiver = new MerchantWebhooks(new WebhookVerifier(), "fixture-only");
        var values = new java.util.LinkedHashMap<>(headers(body, ts)); values.put("x-lunixi-event-type", List.of("payment.captured"));
        assertThrows(RuntimeException.class, () -> receiver.verify(body.getBytes(StandardCharsets.UTF_8), values));
        values.put("X-Lunixi-Event-Id", List.of("event"));
        assertThrows(RuntimeException.class, () -> receiver.verify(body.getBytes(StandardCharsets.UTF_8), values));
    }
}
