package com.mnk.identipatia.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "app.standard-session")
public class StandardSessionProperties {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private String pepper;
    private Duration inactivityTimeout;
    private Duration absoluteTimeout;
    private String cookieName = "IDENTIPAT_STANDARD_SESSION";
    private boolean cookieSecure;
    private String consentCurrentVersion;
    private String consentDocumentSha256;

    @PostConstruct
    void validate() {
        if (pepper == null || Base64.getDecoder().decode(pepper).length < 32) {
            throw new IllegalStateException("STANDARD session pepper must be Base64 with at least 32 decoded bytes");
        }
        if (inactivityTimeout == null || inactivityTimeout.isZero() || inactivityTimeout.isNegative()) {
            throw new IllegalStateException("STANDARD session inactivity timeout must be positive");
        }
        if (absoluteTimeout == null || absoluteTimeout.compareTo(inactivityTimeout) < 0) {
            throw new IllegalStateException("STANDARD session absolute timeout must be at least the inactivity timeout");
        }
        if (consentCurrentVersion == null || consentCurrentVersion.isBlank()) {
            throw new IllegalStateException("Current consent version is required");
        }
        if (consentDocumentSha256 == null || !SHA256_HEX.matcher(consentDocumentSha256).matches()) {
            throw new IllegalStateException("Consent document hash must be lowercase SHA-256 hex");
        }
    }

    public byte[] decodedPepper() {
        return Base64.getDecoder().decode(pepper);
    }

    public String getPepper() { return pepper; }
    public void setPepper(String pepper) { this.pepper = pepper; }
    public Duration getInactivityTimeout() { return inactivityTimeout; }
    public void setInactivityTimeout(Duration inactivityTimeout) { this.inactivityTimeout = inactivityTimeout; }
    public Duration getAbsoluteTimeout() { return absoluteTimeout; }
    public void setAbsoluteTimeout(Duration absoluteTimeout) { this.absoluteTimeout = absoluteTimeout; }
    public String getCookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
    public String getConsentCurrentVersion() { return consentCurrentVersion; }
    public void setConsentCurrentVersion(String consentCurrentVersion) { this.consentCurrentVersion = consentCurrentVersion; }
    public String getConsentDocumentSha256() { return consentDocumentSha256; }
    public void setConsentDocumentSha256(String consentDocumentSha256) { this.consentDocumentSha256 = consentDocumentSha256; }
}
