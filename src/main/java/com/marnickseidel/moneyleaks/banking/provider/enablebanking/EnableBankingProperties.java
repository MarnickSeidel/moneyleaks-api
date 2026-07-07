package com.marnickseidel.moneyleaks.banking.provider.enablebanking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Enable Banking API (<a href="https://enablebanking.com">enablebanking.com</a>).
 *
 * <p>Unlike GoCardless (which uses a secret id/key pair), Enable Banking authenticates every
 * request with a short-lived JWT (RS256) signed by the RSA private key of a registered
 * <em>Application</em>. The JWT header's {@code kid} is the Application ID obtained from the
 * Enable Banking control panel after uploading the matching self-signed certificate.
 *
 * <p>Secrets (the Application ID and, above all, the private key) must be supplied via
 * environment variables / files and never committed. Provide the key either as a path to a
 * PEM file ({@link #privateKeyPath}) or inline via {@link #privateKeyPem} (e.g. from a secret
 * store); the path takes precedence when both are set.
 *
 * @see <a href="https://enablebanking.com/docs/api/reference/">Enable Banking API reference</a>
 */
@ConfigurationProperties(prefix = "enablebanking")
public class EnableBankingProperties {

    /** Base URL of the API. Defaults to the production host. */
    private String apiUrl = "https://api.enablebanking.com";

    /** Application ID from the Enable Banking control panel; used as the JWT {@code kid}. */
    private String applicationId;

    /** Filesystem path to the RSA private key (PKCS#8 PEM) matching the uploaded certificate. */
    private String privateKeyPath;

    /**
     * Inline RSA private key (PKCS#8 PEM contents), as an alternative to {@link #privateKeyPath}
     * when the key is delivered through an environment variable / secret manager. Ignored when
     * {@link #privateKeyPath} is set.
     */
    private String privateKeyPem;

    /** URL Enable Banking redirects the user back to after bank authentication; must match a redirect URL registered for the Application. */
    private String redirectUrl = "http://localhost:8080/api/bank-connections/callback";

    /** Default ISO-3166 country used when listing ASPSPs / starting authorisation (NL for this app). */
    private String country = "NL";

    /** How long the granted consent should remain valid, in days (Enable Banking caps this per ASPSP, commonly 90). */
    private int consentValidDays = 90;

    /** PSU type used when starting authorisation: {@code personal} or {@code business}. */
    private String psuType = "personal";

    /** Lifetime of a generated authorisation JWT, in seconds (Enable Banking allows up to 3600). */
    private long jwtTtlSeconds = 3600;

    /**
     * When true, and no real credentials are configured, the adapter serves deterministic sample
     * data so the connection/sync endpoints and the shared detection pipeline can be exercised
     * locally without credentials. MUST be false in production.
     */
    private boolean sampleDataEnabled = true;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getConsentValidDays() {
        return consentValidDays;
    }

    public void setConsentValidDays(int consentValidDays) {
        this.consentValidDays = consentValidDays;
    }

    public String getPsuType() {
        return psuType;
    }

    public void setPsuType(String psuType) {
        this.psuType = psuType;
    }

    public long getJwtTtlSeconds() {
        return jwtTtlSeconds;
    }

    public void setJwtTtlSeconds(long jwtTtlSeconds) {
        this.jwtTtlSeconds = jwtTtlSeconds;
    }

    public boolean isSampleDataEnabled() {
        return sampleDataEnabled;
    }

    public void setSampleDataEnabled(boolean sampleDataEnabled) {
        this.sampleDataEnabled = sampleDataEnabled;
    }

    /** True once an Application ID and a private key (path or inline PEM) have been configured. */
    public boolean hasCredentials() {
        boolean hasApplicationId = applicationId != null && !applicationId.isBlank();
        boolean hasKey = (privateKeyPath != null && !privateKeyPath.isBlank())
                || (privateKeyPem != null && !privateKeyPem.isBlank());
        return hasApplicationId && hasKey;
    }
}
