package com.lunixi.example;

import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class WebConfiguration {
    @Bean public PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean public UserDetailsService customers(DataSource source) { return new JdbcUserDetailsManager(source); }
    @Bean public SecurityFilterChain security(HttpSecurity http) throws Exception {
        var patterns = org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults();
        var webhook = patterns.matcher(org.springframework.http.HttpMethod.POST, "/webhooks/lunixi");
        var informationalReturn = patterns.matcher(org.springframework.http.HttpMethod.POST, "/payments/{id:[1-9][0-9]*}/return");
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/assets/**", "/error").permitAll()
                .requestMatchers(webhook, informationalReturn).permitAll()
                .anyRequest().hasRole("CUSTOMER"))
            .csrf(csrf -> csrf.ignoringRequestMatchers(webhook, informationalReturn))
            .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/orders", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login"))
            .build();
    }
    @Bean public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH); resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("tr"))); return resolver;
    }
    @Bean @ConditionalOnProperty(name = "sample.initialize-disposable", havingValue = "true")
    public ApplicationRunner explicitDisposableInitialization(NativeSchema schema) { return args -> schema.initializeDisposable(); }
}
