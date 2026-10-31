package com.lunixi.example;

import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings, independent from signing and token-storage secrets. */
@ConfigurationProperties("sample")
public final class SampleProperties implements InitializingBean {
    private String merchantId = "";
    private String publicBaseUrl = "";
    private String jdbcUrl = "";
    private boolean initializeDisposable;
    private String alicePassword = "";
    private String bobPassword = "";
    private String checkoutMode = "hosted";
    private boolean preauth;
    private String orderLocale = "en";
    @Override public void afterPropertiesSet() {
        if (merchantId.isBlank() || jdbcUrl.isBlank() || !Set.of("hosted", "embedded").contains(checkoutMode) || !Set.of("en", "tr").contains(orderLocale)) { throw new IllegalArgumentException("Configure merchant, explicit JDBC URL, checkout mode and locale."); }
        URI origin = URI.create(publicBaseUrl);
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null) { throw new IllegalArgumentException("An operator-configured HTTPS callback base URL is required."); }
        if (publicBaseUrl.endsWith("/")) { publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1); }
    }
    public String getMerchantId() { return merchantId; } public void setMerchantId(String value) { merchantId = value; }
    public String getPublicBaseUrl() { return publicBaseUrl; } public void setPublicBaseUrl(String value) { publicBaseUrl = value; }
    public String getJdbcUrl() { return jdbcUrl; } public void setJdbcUrl(String value) { jdbcUrl = value; }
    public boolean isInitializeDisposable() { return initializeDisposable; } public void setInitializeDisposable(boolean value) { initializeDisposable = value; }
    public String getAlicePassword() { return alicePassword; } public void setAlicePassword(String value) { alicePassword = value; }
    public String getBobPassword() { return bobPassword; } public void setBobPassword(String value) { bobPassword = value; }
    public String getCheckoutMode() { return checkoutMode; } public void setCheckoutMode(String value) { checkoutMode = value; }
    public boolean isPreauth() { return preauth; } public void setPreauth(boolean value) { preauth = value; }
    public String getOrderLocale() { return orderLocale; } public void setOrderLocale(String value) { orderLocale = value; }
    @Override public String toString() { return "SampleProperties[credentials=[REDACTED]]"; }
}
