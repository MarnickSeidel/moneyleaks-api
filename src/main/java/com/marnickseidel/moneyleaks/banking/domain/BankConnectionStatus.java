package com.marnickseidel.moneyleaks.banking.domain;

/**
 * Lifecycle of an Open Banking connection, independent of any specific provider.
 *
 * <ul>
 *   <li>{@code CREATED} - session started, user has not yet granted consent.</li>
 *   <li>{@code ACTIVE} - consent granted, accounts and transactions can be fetched.</li>
 *   <li>{@code EXPIRED} - consent lapsed (providers cap access, e.g. 90 days); needs refresh.</li>
 *   <li>{@code DISCONNECTED} - revoked by the user or torn down by us.</li>
 * </ul>
 */
public enum BankConnectionStatus {
    CREATED,
    ACTIVE,
    EXPIRED,
    DISCONNECTED
}
