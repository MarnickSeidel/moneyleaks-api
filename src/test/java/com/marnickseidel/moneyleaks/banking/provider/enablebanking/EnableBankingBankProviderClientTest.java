package com.marnickseidel.moneyleaks.banking.provider.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;
import com.marnickseidel.moneyleaks.banking.service.BankProviderRegistry;
import com.marnickseidel.moneyleaks.banking.service.BankSyncService;
import com.marnickseidel.moneyleaks.banking.service.TransactionMapper;
import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.service.StatementProcessingService;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnableBankingBankProviderClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void reportsProviderKey() {
        assertEquals("enablebanking", newClient(true).providerKey());
    }

    @Test
    void mapsDebitTransactionToNegativeAmountWithDirectDebitHint() throws Exception {
        JsonNode tx = MAPPER.readTree("""
                {
                  "transaction_id": "eb-1",
                  "transaction_amount": { "currency": "EUR", "amount": "12.99" },
                  "credit_debit_indicator": "DBIT",
                  "booking_date": "2026-03-05",
                  "creditor": { "name": "Netflix International B.V." },
                  "creditor_account": { "iban": "LU810670006550194759" },
                  "bank_transaction_code": { "code": "PMNT", "sub_code": "DDBT", "description": "SEPA Direct Debit" },
                  "remittance_information": ["NETFLIX.COM", "subscription"]
                }
                """);

        ExternalTransaction external = EnableBankingBankProviderClient.mapTransaction(tx);

        // Enable Banking reports positive magnitudes; DBIT must become negative for the app.
        assertEquals(new BigDecimal("-12.99"), external.amount());
        assertEquals("EUR", external.currency());
        assertEquals(LocalDate.of(2026, 3, 5), external.bookingDate());
        assertEquals("Netflix International B.V.", external.counterpartyName());
        assertEquals("LU810670006550194759", external.counterpartyIban());
        assertEquals("NETFLIX.COM subscription", external.description());

        // The flattened bank transaction code feeds the SHARED classifier - no second engine.
        ParsedTransaction parsed = mapper().toParsedTransaction(external);
        assertEquals(PaymentMethod.DIRECT_DEBIT, parsed.paymentMethod());
        assertEquals("NETFLIX", parsed.merchantNormalized());
    }

    @Test
    void mapsCreditTransactionToPositiveAmountUsingDebtorAsCounterparty() throws Exception {
        JsonNode tx = MAPPER.readTree("""
                {
                  "entry_reference": "eb-salary",
                  "transaction_amount": { "currency": "EUR", "amount": "2500.00" },
                  "credit_debit_indicator": "CRDT",
                  "booking_date": "2026-03-25",
                  "debtor": { "name": "Employer B.V." },
                  "debtor_account": { "iban": "NL22EMPL0000000001" },
                  "bank_transaction_code": { "code": "PMNT", "sub_code": "RCDT" },
                  "remittance_information": ["Salary payment"]
                }
                """);

        ExternalTransaction external = EnableBankingBankProviderClient.mapTransaction(tx);

        assertEquals(new BigDecimal("2500.00"), external.amount());
        assertEquals("Salary payment", external.description());
        assertEquals("Employer B.V.", external.counterpartyName());
        assertEquals("NL22EMPL0000000001", external.counterpartyIban());
        assertEquals("eb-salary", external.externalId());
    }

    @Test
    void mapsAccountUsingUidAndIban() throws Exception {
        JsonNode account = MAPPER.readTree("""
                {
                  "uid": "07cc67f4-45d6-494b-adac-09b5cbc7e2b5",
                  "account_id": { "iban": "NL00SAMP0123456789" },
                  "name": "Betaalrekening",
                  "currency": "EUR"
                }
                """);

        ExternalAccount mapped = EnableBankingBankProviderClient.mapAccount(account);

        assertEquals("07cc67f4-45d6-494b-adac-09b5cbc7e2b5", mapped.externalAccountId());
        assertEquals("NL00SAMP0123456789", mapped.iban());
        assertEquals("Betaalrekening", mapped.name());
        assertEquals("EUR", mapped.currency());
    }

    @Test
    void sampleModeStartsConnectionWithConsentUrl() {
        BankConnectionSession session = newClient(true)
                .startConnection(new StartConnectionCommand("Nordea", "http://cb", "local-user"));

        assertNotNull(session.externalConnectionId());
        assertNotNull(session.consentUrl());
        assertEquals(BankConnectionStatus.CREATED, session.status());
        assertNotNull(session.expiresAt());
    }

    @Test
    void sampleModeReturnsAccountsAndSignedTransactions() {
        EnableBankingBankProviderClient client = newClient(true);

        List<ExternalAccount> accounts = client.getAccounts("sample-session");
        assertFalse(accounts.isEmpty());

        List<ExternalTransaction> transactions = client.getTransactions(
                accounts.getFirst().externalAccountId(), LocalDate.now().minusDays(90), LocalDate.now());
        assertFalse(transactions.isEmpty());
        assertTrue(transactions.stream().anyMatch(t -> t.amount().signum() < 0));
    }

    @Test
    void sampleSyncFeedsSharedPipelineAndDetectsSubscription() {
        EnableBankingBankProviderClient client = newClient(true);
        RecordingStatementProcessingService pipeline = new RecordingStatementProcessingService();
        pipeline.result = 2;

        BankProviderRegistry registry = new BankProviderRegistry(List.of(client));
        BankSyncService syncService = new BankSyncService(registry, mapper(), pipeline);

        com.marnickseidel.moneyleaks.banking.domain.BankConnection connection =
                new com.marnickseidel.moneyleaks.banking.domain.BankConnection();
        connection.setId(1L);
        connection.setProvider("enablebanking");
        connection.setExternalConnectionId("sample-session");
        connection.setStatus(BankConnectionStatus.ACTIVE);

        BankSyncService.BankSyncResult result = syncService.sync(connection);

        assertEquals(1, result.accountsSynced());
        assertTrue(result.transactionsImported() > 0);
        assertEquals(1, pipeline.ingestCalls);
        // Netflix debits mapped through the shared normalizer before ingestion.
        assertTrue(pipeline.captured.stream()
                .anyMatch(t -> "NETFLIX".equals(t.merchantNormalized())
                        && t.paymentMethod() == PaymentMethod.DIRECT_DEBIT));
    }

    private TransactionMapper mapper() {
        return new TransactionMapper(new MerchantNormalizer(), new PaymentMethodClassifier());
    }

    private EnableBankingBankProviderClient newClient(boolean sampleDataEnabled) {
        EnableBankingProperties properties = new EnableBankingProperties();
        properties.setApiUrl("https://api.enablebanking.com");
        properties.setSampleDataEnabled(sampleDataEnabled);
        EnableBankingJwtService jwtService = new EnableBankingJwtService(properties, MAPPER);
        return new EnableBankingBankProviderClient(properties, jwtService, MAPPER, RestClient.builder());
    }

    /**
     * Test double for the concrete pipeline seam - mirrors the one in {@code BankSyncServiceTest}
     * to avoid mocking a concrete class while asserting Open Banking sync reuses the existing
     * ingestion + detection entry point.
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
