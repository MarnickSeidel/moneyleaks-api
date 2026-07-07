package com.marnickseidel.moneyleaks.banking.provider.gocardless;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the GoCardless Bank Account Data API (formerly Nordigen).
 *
 * <p>Credentials are obtained from the GoCardless Bank Account Data portal and should be
 * supplied via environment variables / secrets in real environments, never committed.
 *
 * @see <a href="https://developer.gocardless.com/bank-account-data/">GoCardless Bank Account Data docs</a>
 */
@ConfigurationProperties(prefix = "gocardless")
public class GoCardlessProperties {

    /** Base URL of the API, e.g. {@code https://bankaccountdata.gocardless.com/api/v2}. */
    private String apiUrl;

    /** Secret ID from the GoCardless Bank Account Data portal. */
    private String secretId;

    /** Secret key from the GoCardless Bank Account Data portal. */
    private String secretKey;

    /**
     * When true, the not-yet-implemented adapter serves deterministic sample data so the
     * connection and sync endpoints (and the shared detection pipeline) can be exercised
     * locally without credentials. MUST be false in production.
     */
    private boolean sampleDataEnabled = true;

    /** URL the provider redirects the user back to after bank authentication. */
    private String redirectUrl = "http://localhost:8080/api/bank-connections/callback";

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isSampleDataEnabled() {
        return sampleDataEnabled;
    }

    public void setSampleDataEnabled(boolean sampleDataEnabled) {
        this.sampleDataEnabled = sampleDataEnabled;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    /**
     * True once real API credentials have been configured. Until then the adapter runs in
     * preparation mode instead of making live calls.
     */
    public boolean hasCredentials() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
