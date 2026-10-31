package com.lunixi.spring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import com.lunixi.sdk.webhook.WebhookEvent;
import com.lunixi.sdk.webhook.WebhookVerifier;

/** Strict raw-byte merchant verification. Durable receipt/application belongs to CommerceService. */
public final class MerchantWebhooks {
    private final WebhookVerifier verifier;
    private final String secret;
    public MerchantWebhooks(WebhookVerifier verifier, String secret) {
        if (secret == null || secret.isBlank()) { throw new IllegalArgumentException("A merchant webhook secret is required."); }
        this.verifier = verifier; this.secret = secret;
    }
    public WebhookEvent verify(byte[] raw, Map<String, List<String>> headers) {
        if (raw == null || raw.length > 1_048_576) { throw new IllegalArgumentException("Webhook size limit exceeded."); }
        Map<String, String> single = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String key = name.toLowerCase(java.util.Locale.ROOT);
            if (!key.startsWith("x-lunixi-")) { return; }
            if (single.containsKey(key) || values == null || values.size() != 1) { throw new IllegalArgumentException("Duplicate webhook headers are forbidden."); }
            single.put(key, values.get(0));
        });
        try {
            String body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            return verifier.verifyMerchant(body, single, secret);
        } catch (CharacterCodingException error) { throw new IllegalArgumentException("Webhook must be valid UTF-8."); }
    }
    @Override public String toString() { return "MerchantWebhooks[secret=[REDACTED]]"; }
}
