package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.provider.enablebanking.EnableBankingBankProviderClient;
import com.marnickseidel.moneyleaks.banking.repository.BankConnectionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankConnectionServiceAuthorizationTest {

    @Test
    void completeAuthorization_exchangesCodeAndActivatesConnection() {
        BankConnectionRepository repository = mock(BankConnectionRepository.class);
        StubEnableBankingClient enableBanking = new StubEnableBankingClient();

        BankConnectionService service = new BankConnectionService(
                repository,
                new BankProviderRegistry(java.util.List.of()),
                null,
                null,
                null,
                enableBanking
        );

        BankConnection connection = new BankConnection();
        connection.setId(7L);
        connection.setProvider(EnableBankingBankProviderClient.PROVIDER_KEY);
        connection.setExternalConnectionId("auth-123");
        connection.setOauthState("state-abc");
        connection.setStatus(BankConnectionStatus.CREATED);

        when(repository.findByOauthState("state-abc")).thenReturn(Optional.of(connection));
        when(repository.save(any(BankConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        BankConnection result = service.completeAuthorization("code-xyz", "state-abc");

        assertEquals("session-from-stub", result.getExternalConnectionId());
        assertEquals(BankConnectionStatus.ACTIVE, result.getStatus());
        assertEquals("code-xyz", enableBanking.lastCode);
    }

    @Test
    void completeAuthorization_rejectsUnknownState() {
        BankConnectionRepository repository = mock(BankConnectionRepository.class);
        when(repository.findByOauthState("missing")).thenReturn(Optional.empty());

        BankConnectionService service = new BankConnectionService(
                repository,
                new BankProviderRegistry(java.util.List.of()),
                null,
                null,
                null,
                new StubEnableBankingClient()
        );

        assertThrows(BankAuthorizationException.class,
                () -> service.completeAuthorization("code", "missing"));
        verify(repository).findByOauthState("missing");
    }

    /** Minimal test double — avoids mocking a concrete Spring @Component on Java 25. */
    private static final class StubEnableBankingClient extends EnableBankingBankProviderClient {
        private String lastCode;

        private StubEnableBankingClient() {
            super(new com.marnickseidel.moneyleaks.banking.provider.enablebanking.EnableBankingProperties(),
                    null,
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    org.springframework.web.client.RestClient.builder());
        }

        @Override
        public BankConnectionSession authorizeSession(String code) {
            lastCode = code;
            return new BankConnectionSession(
                    "session-from-stub",
                    null,
                    BankConnectionStatus.ACTIVE,
                    Instant.parse("2026-10-01T00:00:00Z")
            );
        }
    }
}
