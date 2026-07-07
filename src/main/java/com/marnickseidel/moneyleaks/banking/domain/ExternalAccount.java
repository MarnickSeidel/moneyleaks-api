package com.marnickseidel.moneyleaks.banking.domain;

/**
 * A bank account exposed by an Open Banking provider, in provider-neutral form.
 *
 * @param externalAccountId provider's opaque account identifier used for later transaction fetches
 * @param iban              account IBAN, when the provider discloses it
 * @param name              human-friendly account name/owner, when available
 * @param currency          ISO-4217 currency code (e.g. {@code EUR})
 */
public record ExternalAccount(
        String externalAccountId,
        String iban,
        String name,
        String currency
) {
}
