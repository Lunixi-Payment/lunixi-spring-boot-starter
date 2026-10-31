package com.lunixi.example;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import com.lunixi.spring.MerchantWebhooks;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class WebhookController {
    private final MerchantWebhooks verifier;
    private final CheckoutFlow flow;
    public WebhookController(MerchantWebhooks verifier, CheckoutFlow flow) { this.verifier = verifier; this.flow = flow; }
    @PostMapping("/webhooks/lunixi") public ResponseEntity<String> webhook(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 1_048_576) { return ResponseEntity.status(413).body(""); }
        // Bound allocation before decoding; @RequestBody would materialize an unbounded body first.
        byte[] raw = request.getInputStream().readNBytes(1_048_577);
        if (raw.length > 1_048_576) { return ResponseEntity.status(413).body(""); }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) { headers.put(name, Collections.list(request.getHeaders(name))); }
        try { flow.webhook(verifier.verify(raw, headers), raw); return ResponseEntity.ok("OK"); }
        catch (RuntimeException error) { return ResponseEntity.status(503).body("Retry delivery"); }
    }
}
