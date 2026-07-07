package com.marnickseidel.moneyleaks.banking.provider.gocardless;

import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoCardlessBankProviderClientTest {

    @Test
    void reportsProviderKey() {
        assertEquals("gocardless", newClient(true).providerKey());
    }

    @Test
    void sampleModeStartsConnectionWithConsentUrl() {
        BankConnectionSession session = newClient(true)
                .startConnection(new StartConnectionCommand("SANDBOXFINANCE_SFIN0000", "http://cb", "local-user"));

        assertNotNull(session.externalConnectionId());
        assertNotNull(session.consentUrl());
        assertEquals(BankConnectionStatus.CREATED, session.status());
        assertNotNull(session.expiresAt());
    }

    @Test
    void sampleModeReturnsAccountsAndTransactions() {
        GoCardlessBankProviderClient client = newClient(true);

        List<ExternalAccount> accounts = client.getAccounts("ext-conn");
        assertFalse(accounts.isEmpty());

        List<ExternalTransaction> transactions =
                client.getTransactions(accounts.getFirst().externalAccountId(),
                        LocalDate.now().minusDays(90), LocalDate.now());
        assertFalse(transactions.isEmpty());
        // Sample outgoing charges keep the negative-amount convention.
        assertTrue(transactions.stream().anyMatch(t -> t.amount().signum() < 0));
    }

    @Test
    void withoutCredentialsAndSampleDataDisabledLiveCallsAreNotImplemented() {
        GoCardlessBankProviderClient client = newClient(false);

        assertThrows(UnsupportedOperationException.class,
                () -> client.getAccounts("ext-conn"));
        assertThrows(UnsupportedOperationException.class,
                () -> client.startConnection(new StartConnectionCommand("X", "http://cb", "local-user")));
    }

    private GoCardlessBankProviderClient newClient(boolean sampleDataEnabled) {
        GoCardlessProperties properties = new GoCardlessProperties();
        properties.setApiUrl("https://bankaccountdata.gocardless.com/api/v2");
        properties.setSampleDataEnabled(sampleDataEnabled);
        return new GoCardlessBankProviderClient(properties);
    }
}
