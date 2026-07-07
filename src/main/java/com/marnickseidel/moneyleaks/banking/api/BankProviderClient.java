package com.marnickseidel.moneyleaks.banking.api;

import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;

import java.time.LocalDate;
import java.util.List;

/**
 * Provider-independent contract for an Open Banking backend (GoCardless Bank Account Data,
 * Plaid, Tink, ...). The core application depends only on this interface, so swapping or
 * adding a provider never touches persistence or subscription detection.
 *
 * <p>Every method deals in the provider-neutral DTOs from
 * {@code com.marnickseidel.moneyleaks.banking.domain}; adapters are responsible for
 * translating their raw API payloads to and from these types.
 */
public interface BankProviderClient {

    /**
     * Stable key identifying this provider (e.g. {@code "gocardless"}). Stored on the
     * {@code BankConnection} so we know which adapter owns a connection.
     */
    String providerKey();

    /**
     * Begin a connection: ask the provider to create a consent/requisition and return the
     * URL the user must visit to authenticate at their bank.
     */
    BankConnectionSession startConnection(StartConnectionCommand command);

    /**
     * Current status of a previously started connection, refreshed from the provider.
     */
    BankConnectionStatus getConnectionStatus(String externalConnectionId);

    /**
     * Accounts that the user granted access to for the given connection.
     */
    List<ExternalAccount> getAccounts(String externalConnectionId);

    /**
     * Booked transactions for one account within the given (inclusive) date range.
     */
    List<ExternalTransaction> getTransactions(String externalAccountId, LocalDate dateFrom, LocalDate dateTo);

    /**
     * Renew access for a connection whose consent has expired, returning a fresh session
     * (typically containing a new consent URL for the user to re-authenticate).
     */
    BankConnectionSession refreshConnection(String externalConnectionId);
}
