package com.lunixi.spring;

import java.net.URI;
import java.util.Base64;
import javax.sql.DataSource;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.TokenStore;
import com.lunixi.sdk.http.HttpTransport;
import com.lunixi.sdk.payment.CommerceService;
import com.lunixi.sdk.payment.LedgerStore;
import com.lunixi.sdk.payment.SecretBox;
import com.lunixi.sdk.webhook.WebhookVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@AutoConfiguration
@EnableConfigurationProperties(LunixiProperties.class)
public class LunixiAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    public LunixiClient lunixiClient(LunixiProperties properties, ObjectProvider<TokenStore> tokenStore, ObjectProvider<HttpTransport> transport) {
        String configured = properties.getBaseUrl();
        if (configured == null) { throw new IllegalArgumentException("Configure lunixi.base-url and its approved origin."); }
        String base = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        URI origin = URI.create(base);
        if (properties.getAllowedOrigins() == null || !properties.getAllowedOrigins().contains(base) || !"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null || !origin.getPath().isEmpty()) { throw new IllegalArgumentException("The gateway must be an explicitly approved HTTPS origin."); }
        LunixiConfig.Builder builder = LunixiConfig.builder().baseUrl(base).keyId(properties.getKeyId()).privateKey(properties.getPrivateKey()).environment(properties.getEnvironment()).timeout(properties.getTimeout()).maxRetries(0);
        tokenStore.ifAvailable(builder::tokenStore); transport.ifAvailable(builder::transport);
        return LunixiClient.create(builder.build());
    }
    @Bean @ConditionalOnMissingBean
    public WebhookVerifier lunixiWebhookVerifier() { return new WebhookVerifier(); }
    @Bean @ConditionalOnMissingBean @ConditionalOnProperty("lunixi.webhook-secret")
    public MerchantWebhooks lunixiMerchantWebhooks(WebhookVerifier verifier, LunixiProperties properties) { return new MerchantWebhooks(verifier, properties.getWebhookSecret()); }
    @Bean @ConditionalOnMissingBean(LedgerStore.class) @ConditionalOnProperty(name = "lunixi.jdbc-ledger-enabled", havingValue = "true")
    public JdbcLedgerStore lunixiJdbcLedgerStore(DataSource dataSource, LunixiProperties properties) { return new JdbcLedgerStore(dataSource, new DataSourceTransactionManager(dataSource), properties.getTablePrefix()); }
    @Bean @ConditionalOnMissingBean @ConditionalOnProperty("lunixi.storage-key")
    public SecretBox lunixiSecretBox(LunixiProperties properties) { return new SecretBox(Base64.getDecoder().decode(properties.getStorageKey())); }
    @Bean @ConditionalOnMissingBean @ConditionalOnBean({LedgerStore.class, SecretBox.class})
    public CommerceService lunixiCommerceService(LunixiClient client, LedgerStore store, SecretBox box) { return new CommerceService(client, store, box); }
}
