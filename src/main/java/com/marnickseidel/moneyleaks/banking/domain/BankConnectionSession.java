package com.marnickseidel.moneyleaks.banking.domain;

import java.time.Instant;

/**
 * The result of starting (or refreshing) a bank connection with a provider.
 *
 * <p>Open Banking uses a redirect-based consent flow: we ask the provider to create a
 * link, the user is sent to {@code consentUrl} to authenticate at their bank, and the
 * provider calls us back once consent is granted. This record carries everything the
 * caller needs to drive that flow, without leaking provider-specific structures.
 *
 * @param externalConnectionId provider's opaque id for this connection/requisition
 * @param consentUrl           URL to redirect the end user to for bank authentication
 * @param status               current status of the connection
 * @param expiresAt            when the granted access will expire, if known
 */
public record BankConnectionSession(
        String externalConnectionId,
        String consentUrl,
        BankConnectionStatus status,
        Instant expiresAt
) {
}
