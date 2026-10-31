package com.lunixi.spring;

import static org.assertj.core.api.Assertions.assertThat;
import com.lunixi.sdk.LunixiClient;
import com.lunixi.sdk.LunixiConfig;
import com.lunixi.sdk.auth.Ed25519KeyPair;
import com.lunixi.sdk.payment.CommerceService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AutoConfigurationTest {
    private ApplicationContextRunner configured() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LunixiAutoConfiguration.class)).withPropertyValues(
            "lunixi.base-url=https://gateway.invalid", "lunixi.allowed-origins[0]=https://gateway.invalid", "lunixi.key-id=test-key", "lunixi.private-key=" + Ed25519KeyPair.generate().privateKeyPem());
    }
    @Test void wiresNativeBeansAndOptionalJdbcWithoutImplicitSchemaChanges() {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:configuration;DB_CLOSE_DELAY=-1");
        configured().withBean(javax.sql.DataSource.class, () -> source).withPropertyValues("lunixi.jdbc-ledger-enabled=true", "lunixi.webhook-secret=fixture-only", "lunixi.storage-key=" + java.util.Base64.getEncoder().encodeToString(new byte[32])).run(context -> {
            assertThat(context).hasSingleBean(LunixiClient.class).hasSingleBean(MerchantWebhooks.class).hasSingleBean(JdbcLedgerStore.class).hasSingleBean(CommerceService.class);
            assertThat(context.getBean(LunixiClient.class).config().environment()).isEqualTo(LunixiConfig.Environment.TEST);
            assertThat(context.getBean(LunixiProperties.class).toString()).doesNotContain("fixture-only");
            assertThat(new org.springframework.jdbc.core.JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='LUNIXI_ATTEMPTS'", Integer.class)).isZero();
        });
    }
    @Test void rejectsUnapprovedAndInsecureOriginsAtStartup() {
        configured().withPropertyValues("lunixi.allowed-origins[0]=https://another.invalid").run(context -> assertThat(context).hasFailed());
        configured().withPropertyValues("lunixi.base-url=http://gateway.invalid", "lunixi.allowed-origins[0]=http://gateway.invalid").run(context -> assertThat(context).hasFailed());
    }
    @Test void applicationClientOverridesAutoConfiguration() {
        LunixiClient provided = LunixiClient.create(LunixiConfig.builder().baseUrl("https://gateway.invalid").keyId("test-key").privateKey(Ed25519KeyPair.generate().privateKeyPem()).build());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LunixiAutoConfiguration.class)).withBean(LunixiClient.class, () -> provided).run(context -> assertThat(context.getBean(LunixiClient.class)).isSameAs(provided));
    }
}
