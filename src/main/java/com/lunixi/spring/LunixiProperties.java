package com.lunixi.spring;

import java.time.Duration;
import java.util.Set;
import com.lunixi.sdk.LunixiConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server configuration. Credential values are never included in diagnostics. */
@ConfigurationProperties("lunixi")
public final class LunixiProperties {
    private String baseUrl;
    private Set<String> allowedOrigins = Set.of();
    private String keyId;
    private String privateKey;
    private String webhookSecret;
    private String storageKey;
    private LunixiConfig.Environment environment = LunixiConfig.Environment.TEST;
    private Duration timeout = Duration.ofSeconds(30);
    private boolean jdbcLedgerEnabled;
    private String tablePrefix = "lunixi_";
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public Set<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(Set<String> value) { allowedOrigins = value; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String value) { keyId = value; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String value) { privateKey = value; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String value) { webhookSecret = value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey = value; }
    public LunixiConfig.Environment getEnvironment() { return environment; }
    public void setEnvironment(LunixiConfig.Environment value) { environment = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public boolean isJdbcLedgerEnabled() { return jdbcLedgerEnabled; }
    public void setJdbcLedgerEnabled(boolean value) { jdbcLedgerEnabled = value; }
    public String getTablePrefix() { return tablePrefix; }
    public void setTablePrefix(String value) { tablePrefix = value; }
    @Override public String toString() { return "LunixiProperties[credentials=[REDACTED]]"; }
}
