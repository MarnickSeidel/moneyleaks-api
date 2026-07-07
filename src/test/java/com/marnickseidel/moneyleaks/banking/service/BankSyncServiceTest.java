package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.service.StatementProcessingService;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankSyncServiceTest {

    private BankProviderClient provider;
    private RecordingStatementProcessingService statementProcessingService;
    private BankSyncService bankSyncService;

    @BeforeEach
    void setUp() {
        provider = mock(BankProviderClient.class);
        when(provider.providerKey()).thenReturn("gocardless");

        statementProcessingService = new RecordingStatementProcessingService();

        BankProviderRegistry registry = new BankProviderRegistry(List.of(provider));
        TransactionMapper mapper = new TransactionMapper(
                new MerchantNormalizer(), new PaymentMethodClassifier());

        bankSyncService = new BankSyncService(registry, mapper, statementProcessingService);
    }

    @Test
    void fetchesMapsAndFeedsExistingPipeline() {
        when(provider.getAccounts("ext-conn-1")).thenReturn(List.of(
                new ExternalAccount("acc-1", "NL00SAMP0123456789", "Current", "EUR")
        ));
        when(provider.getTransactions(anyString(), any(), any())).thenReturn(List.of(
                new ExternalTransaction("t1", LocalDate.of(2026, 1, 5), new BigDecimal("-12.99"),
                        "EUR", "NETFLIX.COM", "Netflix", null, "DD"),
                new ExternalTransaction("t2", LocalDate.of(2026, 2, 5), new BigDecimal("-12.99"),
                        "EUR", "NETFLIX.COM", "Netflix", null, "DD")
        ));
        statementProcessingService.result = 1;

        BankSyncService.BankSyncResult result = bankSyncService.sync(connection());

        assertEquals(1, result.accountsSynced());
        assertEquals(2, result.transactionsImported());
        assertEquals(1, result.subscriptionsDetected());

        List<ParsedTransaction> ingested = statementProcessingService.captured;
        assertNotNull(ingested);
        assertEquals(2, ingested.size());
        // Mapping went through the shared normalizer before hitting the pipeline.
        assertEquals("NETFLIX", ingested.getFirst().merchantNormalized());
        // Exactly one ingestion call - the sync service never runs its own detection.
        assertEquals(1, statementProcessingService.ingestCalls);
    }

    @Test
    void syncsWithNoAccountsStillDelegatesEmptyBatch() {
        when(provider.getAccounts(anyString())).thenReturn(List.of());
        statementProcessingService.result = 0;

        BankSyncService.BankSyncResult result = bankSyncService.sync(connection());

        assertEquals(0, result.accountsSynced());
        assertEquals(0, result.transactionsImported());
        assertEquals(1, statementProcessingService.ingestCalls);
        assertEquals(List.of(), statementProcessingService.captured);
    }

    private BankConnection connection() {
        BankConnection connection = new BankConnection();
        connection.setId(1L);
        connection.setProvider("gocardless");
        connection.setExternalConnectionId("ext-conn-1");
        connection.setStatus(BankConnectionStatus.ACTIVE);
        return connection;
    }

    /**
     * Hand-rolled test double for the concrete pipeline seam. Avoids mocking a concrete class
     * (unsupported by the bundled Byte Buddy on newer JDKs) while still asserting that Open
     * Banking sync delegates to the existing ingestion + detection entry point.
     */
    private static final class RecordingStatementProcessingService extends StatementProcessingService {

        private List<ParsedTransaction> captured;
        private int ingestCalls;
        private int result;

        private RecordingStatementProcessingService() {
            super(null, null, null, null, null);
        }

        @Override
        public int ingestOpenBankingTransactions(List<ParsedTransaction> transactions) {
            this.captured = transactions;
            this.ingestCalls++;
            return result;
        }
    }
}
