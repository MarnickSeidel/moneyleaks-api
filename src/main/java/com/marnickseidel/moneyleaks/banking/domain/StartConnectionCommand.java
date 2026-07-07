package com.marnickseidel.moneyleaks.banking.domain;

/**
 * Input for starting a bank connection. Provider-neutral so the same call works for any
 * Open Banking backend.
 *
 * @param institutionId provider's identifier for the target bank (from its institutions list)
 * @param redirectUrl   URL the provider should return the user to after bank authentication
 * @param userReference caller-side reference for the end user (placeholder until real auth exists)
 * @param oauthState    opaque value echoed back on redirect so the callback can find this connection
 */
public record StartConnectionCommand(
        String institutionId,
        String redirectUrl,
        String userReference,
        String oauthState
) {
    public StartConnectionCommand(String institutionId, String redirectUrl, String userReference) {
        this(institutionId, redirectUrl, userReference, null);
    }
}
