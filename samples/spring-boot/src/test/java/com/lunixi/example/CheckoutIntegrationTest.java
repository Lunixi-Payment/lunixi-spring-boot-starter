package com.lunixi.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.json.Json;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CheckoutIntegrationTest.TransportConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckoutIntegrationTest {
    private static final String PRIVATE_KEY = Ed25519KeyPair.generate().privateKeyPem();
    private static final String SECRET = "fixture-webhook-signing-secret";
    private static final String ALICE = "only-test-alice-password-strong";
    private static final String BOB = "only-test-bob-password-different";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NativeSchema schema;
    @Autowired SampleProperties sample;
    @Autowired GatewayFixture gateway;
    @Autowired OrderRepository orders;
    @Autowired CheckoutFlow flow;
    @Autowired PaidOutbox outbox;
    @TestConfiguration static class TransportConfiguration {
        @Bean GatewayFixture gatewayFixture() { return new GatewayFixture(); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:lunixi-native-web;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        properties.add("spring.datasource.username", () -> "sa"); properties.add("spring.datasource.password", () -> "");
        properties.add("sample.jdbc-url", () -> "jdbc:h2:mem:lunixi-native-web");
        properties.add("sample.merchant-id", () -> "merchant-a"); properties.add("sample.public-base-url", () -> "https://billing.example");
        properties.add("sample.initialize-disposable", () -> "false"); properties.add("sample.alice-password", () -> ALICE); properties.add("sample.bob-password", () -> BOB);
        properties.add("lunixi.key-id", () -> "fixture-key"); properties.add("lunixi.private-key", () -> PRIVATE_KEY);
        properties.add("lunixi.webhook-secret", () -> SECRET); properties.add("lunixi.storage-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }
    @BeforeAll void noImplicitDdlAndExplicitDisposableInstall() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('SAMPLE_ORDERS','LUNIXI_ATTEMPTS')", Integer.class)).isZero();
        sample.setInitializeDisposable(true); schema.initializeDisposable(); sample.setInitializeDisposable(false);
    }
    @BeforeEach void reset() {
        jdbc.execute("ALTER TABLE lunixi_attempts DROP CONSTRAINT IF EXISTS deny_paid");
        jdbc.update("DELETE FROM sample_outbox"); jdbc.update("DELETE FROM sample_orders"); jdbc.update("DELETE FROM lunixi_events"); jdbc.update("DELETE FROM lunixi_attempts");
        insert(1, "alice"); insert(2, "bob"); gateway.reset();
    }
    private void insert(long id, String user) { jdbc.update("INSERT INTO sample_orders(id,owner_username,merchant_id,environment,base_amount,currency,checkout_mode,preauth,locale) VALUES (?,?,'merchant-a','TEST',1990,'TRY','hosted',FALSE,'en')", id, user); }
    private void start() throws Exception { mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isSeeOther()); }
    private Signed signed(String id, String extra) throws Exception { return signed(id, extra, "TEST", Instant.now().toString()); }
    private Signed signed(String id, String extra, String environment, String timestamp) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>(); data.put("intentId", "pi-1"); data.put("orderId", "spring:order:1"); if (extra != null) { data.put("extra", extra); }
        Map<String, Object> envelope = new LinkedHashMap<>(); envelope.put("id", id); envelope.put("type", "payment.captured"); envelope.put("environment", environment); envelope.put("data", data);
        String body = Json.stringify(envelope);
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = java.util.HexFormat.of().formatHex(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return new Signed(body, id, timestamp, "v2=" + digest);
    }
    private MockHttpServletRequestBuilder webhook(Signed signed) { return post("/webhooks/lunixi").contentType("application/json").content(signed.body()).header("X-Lunixi-Event-Id", signed.id()).header("X-Lunixi-Signature-Timestamp", signed.timestamp()).header("X-Lunixi-Signature", signed.signature()); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    @Test void nativeLoginUsesJdbcPasswordHashAndSession() throws Exception {
        mvc.perform(formLogin().user("alice").password(ALICE)).andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername("alice"));
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE username='alice'", String.class)).startsWith("{bcrypt}").doesNotContain(ALICE);
    }
    @Test void customerOwnershipCsrfAndVerbGatesPrecedeFinancialNetwork() throws Exception {
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        mvc.perform(post("/payments/1").with(user("bob").roles("CUSTOMER")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/payments/1").with(user("staff").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/payments/1").with(user("alice").roles("CUSTOMER"))).andExpect(status().isMethodNotAllowed());
        assertThat(gateway.calls).isEmpty();
    }
    @Test void hostedCheckoutHasStableReservationAndEncryptedBrowserToken() throws Exception {
        start();
        assertThat(gateway.creates()).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT payload FROM lunixi_attempts", String.class);
        assertThat(payload).doesNotContain("fixture-browser-token");
        assertThat(payload).doesNotContain("hosted-fixture-session", "https://pay.lunixi.com");
        Map<String, Object> body = Json.parseObject(gateway.calls.stream().filter(call -> call.url().endsWith("/payments/intents")).findFirst().orElseThrow().body());
        assertThat(body.get("amount")).isEqualTo("1990"); assertThat(body.get("callbackUrl")).isEqualTo("https://billing.example/payments/1/return");
        assertThat(body).doesNotContainKeys("price", "paidPrice", "environment");
    }
    @Test void embeddedIsTheApplicationDefaultAndExplicitStoredHostedChoiceUsesOnlyTheBackendUrl() throws Exception {
        assertThat(sample.getCheckoutMode()).isEqualTo("embedded");
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", gateway.paymentPageUrl));
        assertThat(gateway.paymentPageUrl).doesNotContain(gateway.token);
        assertThat(gateway.creates()).isEqualTo(1);
    }
    @Test void missingBackendHostedUrlFailsClosedWithoutRotatingTheAttemptWhileEmbeddingStillWorks() throws Exception {
        gateway.paymentPageUrl = null;
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict());
        assertThat(gateway.creates()).isEqualTo(1);
        jdbc.update("UPDATE sample_orders SET checkout_mode='embedded' WHERE id=2");
        mvc.perform(post("/payments/2").with(user("bob").roles("CUSTOMER")).with(csrf())).andExpect(status().isOk());
        assertThat(orders.load(1).state()).isEqualTo("pending");
    }
    @Test void embeddedFormEscapesTokenAndUsesExactOfficialSchemaAsset() throws Exception {
        jdbc.update("UPDATE sample_orders SET checkout_mode='embedded' WHERE id=1"); gateway.token = "\"/><script>alert(1)</script>";
        String html = mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-token=", "https://cdn.lunixi.com/lunixi-checkout.min.js", "/assets/checkout.js").doesNotContain("<script>alert(1)</script>");
    }
    @Test void forgedBrowserReturnNeverChangesPaymentStateOrCallsGateway() throws Exception {
        mvc.perform(get("/payments/1/return").param("status", "CAPTURED").param("paymentId", "forged").param("amount", "1990").with(user("alice").roles("CUSTOMER"))).andExpect(status().isOk());
        assertThat(orders.load(1).state()).isEqualTo("pending"); assertThat(gateway.calls).isEmpty();
        mvc.perform(get("/payments/2/return").with(user("alice").roles("CUSTOMER"))).andExpect(status().isNotFound());
    }
    @Test void verifiedWebhookAuthenticatesGetAndCommitsOrderInboxOutboxOnce() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); Signed event = signed("evt-captured", null);
        mvc.perform(webhook(event)).andExpect(status().isOk()); mvc.perform(webhook(event)).andExpect(status().isOk());
        assertThat(orders.load(1).state()).isEqualTo("paid"); assertThat(count("lunixi_events")).isEqualTo(1); assertThat(count("sample_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload FROM sample_outbox", String.class)).doesNotContain("pi-1", "order.paid");
    }
    @Test void finalLedgerWriteFailureRollsBackNativeOrderInboxAndOutboxTogether() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); Signed event = signed("evt-rollback", null);
        jdbc.execute("ALTER TABLE lunixi_attempts ADD CONSTRAINT deny_paid CHECK (payload NOT LIKE '%\"state\":\"paid\"%')");
        mvc.perform(webhook(event)).andExpect(status().isServiceUnavailable());
        assertThat(orders.load(1).state()).isEqualTo("pending"); assertThat(count("lunixi_events")).isZero(); assertThat(count("sample_outbox")).isZero();
        jdbc.execute("ALTER TABLE lunixi_attempts DROP CONSTRAINT deny_paid"); mvc.perform(webhook(event)).andExpect(status().isOk()); assertThat(orders.load(1).state()).isEqualTo("paid");
    }
    @Test void tamperedStaleDuplicateHeadersAndOversizedBodyAreRejected() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); Signed event = signed("evt-security", null);
        mvc.perform(webhook(event).content(event.body() + " ")).andExpect(status().isServiceUnavailable());
        mvc.perform(webhook(event).header("X-Lunixi-Signature", "another-value")).andExpect(status().isServiceUnavailable());
        mvc.perform(webhook(signed("evt-stale", null, "TEST", Instant.now().minusSeconds(900).toString()))).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/webhooks/lunixi").content(new byte[1_048_577])).andExpect(status().is(413));
        assertThat(count("lunixi_events")).isZero(); assertThat(orders.load(1).state()).isEqualTo("pending");
    }
    @Test void signedEventIdCollisionDoesNotSilentlyAcknowledgeDifferentContent() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); mvc.perform(webhook(signed("evt-collision", null))).andExpect(status().isOk());
        mvc.perform(webhook(signed("evt-collision", "different"))).andExpect(status().isServiceUnavailable()); assertThat(count("lunixi_events")).isEqualTo(1);
    }
    @Test void changedNativeOrderCannotBePaidByOldIntent() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); jdbc.update("UPDATE sample_orders SET base_amount=2990 WHERE id=1");
        mvc.perform(webhook(signed("evt-edit", null))).andExpect(status().isServiceUnavailable()); assertThat(orders.load(1).state()).isEqualTo("pending"); assertThat(count("lunixi_events")).isZero();
    }
    @Test void manualHoldSurvivesCaptureAndRequiresNativeReleaseBeforeOutbox() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); jdbc.update("UPDATE sample_orders SET manual_hold=TRUE WHERE id=1");
        mvc.perform(webhook(signed("evt-hold", null))).andExpect(status().isOk()); assertThat(orders.load(1).state()).isEqualTo("review"); assertThat(count("sample_outbox")).isZero();
        jdbc.update("UPDATE sample_orders SET manual_hold=FALSE WHERE id=1"); flow.reconcile(1); assertThat(orders.load(1).state()).isEqualTo("paid"); assertThat(count("sample_outbox")).isEqualTo(1);
    }
    @Test void partialCaptureAndFailedUncertainOutcomeNeverFulfill() throws Exception {
        start(); gateway.settle("PARTIALLY_CAPTURED", 1000, 0); flow.reconcile(1); assertThat(orders.load(1).state()).isEqualTo("partially_captured"); assertThat(count("sample_outbox")).isZero();
    }
    @Test void failedUnknownRemainsReviewWithoutAutomaticNewKey() throws Exception {
        start(); gateway.settle("FAILED", 0, 0); flow.reconcile(1); assertThat(orders.load(1).state()).isEqualTo("review");
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict()); assertThat(gateway.creates()).isEqualTo(1);
    }
    @Test void installmentGrossDoesNotOvercreditNativePrincipal() throws Exception {
        start(); gateway.gross(2100); gateway.settle("CAPTURED", 2100, 0); mvc.perform(webhook(signed("evt-fee", null))).andExpect(status().isOk());
        NativeOrder order = orders.load(1); assertThat(order.amount()).isEqualTo(1990); assertThat(order.charged()).isEqualTo(2100); assertThat(order.captured()).isEqualTo(2100);
    }
    @Test void lostCreateResponseKeepsExactKeyAndRequestAcrossRetry() throws Exception {
        gateway.loseCreate = true; mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict()); start();
        List<GatewayFixture.Call> creates = gateway.calls.stream().filter(call -> call.url().endsWith("/payments/intents")).toList();
        assertThat(creates).hasSize(2); assertThat(creates.get(0).body()).isEqualTo(creates.get(1).body()); assertThat(creates.get(0).headers().get("Idempotency-Key")).isEqualTo(creates.get(1).headers().get("Idempotency-Key"));
    }
    @Test void retentionFenceBlocksUnknownPostWhileSignedRecoveryStillWorks() throws Exception {
        gateway.loseCreate = true; mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict());
        Map<String, Object> record = Json.parseObject(jdbc.queryForObject("SELECT payload FROM lunixi_attempts", String.class)); record.put("createdAt", Instant.now().minusSeconds(86401).getEpochSecond()); jdbc.update("UPDATE lunixi_attempts SET payload=?", Json.stringify(record));
        mvc.perform(post("/payments/1").with(user("alice").roles("CUSTOMER")).with(csrf())).andExpect(status().isConflict()); assertThat(gateway.creates()).isEqualTo(1);
        gateway.settle("CAPTURED", 1990, 0); mvc.perform(webhook(signed("evt-recovery", null))).andExpect(status().isOk()); assertThat(orders.load(1).state()).isEqualTo("paid");
    }
    @Test void postcommitOutboxClaimIsDurableAndUncertainIsNotBlindlyRetried() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); mvc.perform(webhook(signed("evt-outbox", null))).andExpect(status().isOk());
        PaidOutbox.ClaimedEvent claimed = outbox.claim(); assertThat(claimed).isNotNull(); assertThat(claimed.event().get("type")).isEqualTo("order.paid"); outbox.acknowledge(claimed.key(), false);
        assertThat(outbox.claim()).isNull(); assertThat(jdbc.queryForObject("SELECT state FROM sample_outbox", String.class)).isEqualTo("uncertain");
    }
    @Test void englishAndTurkishDoNotDependOnServerSystemLocale() throws Exception {
        String english = mvc.perform(get("/login").header("Accept-Language", "en")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String turkish = mvc.perform(get("/login").header("Accept-Language", "tr")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(english).contains("Sign in").doesNotContain("Oturum aç"); assertThat(turkish).contains("Oturum aç");
    }
    @Test void validSignatureFromWrongEnvironmentIsNotPaymentAuthority() throws Exception {
        start(); gateway.settle("CAPTURED", 1990, 0); long before = gateway.calls.size();
        mvc.perform(webhook(signed("evt-wrong-scope", null, "LIVE", Instant.now().toString()))).andExpect(status().isServiceUnavailable());
        assertThat(gateway.calls.size()).isEqualTo(before); assertThat(count("lunixi_events")).isZero(); assertThat(orders.load(1).state()).isEqualTo("pending");
    }
    @Test void anonymousFormReturnIsOnlyBoundedSameOrigin303AndGetKeepsAuthorization() throws Exception {
        String body = "status=CAPTURED&paymentId=forged&_signature=not-webhook-proof";
        String response = mvc.perform(post("/payments/1/return").contentType("application/x-www-form-urlencoded").content(body)).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/payments/1/return")).andReturn().getResponse().getContentAsString();
        assertThat(response).isEmpty(); assertThat(orders.load(1).state()).isEqualTo("pending"); assertThat(gateway.calls).isEmpty(); assertThat(count("lunixi_attempts")).isZero();
        mvc.perform(get("/payments/1/return")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/payments/1/return").with(user("bob").roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(post("/payments/999/return").content(body)).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/payments/999/return"));
        mvc.perform(post("/payments/1/return").content(new byte[65537])).andExpect(status().is(413));
    }
    record Signed(String body, String id, String timestamp, String signature) { }
}
