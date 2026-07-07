package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.service.StatementProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls transactions from a connected bank and funnels them into the shared detection
 * pipeline. The flow is deliberately thin:
 *
 * <pre>
 *   BankProviderClient (accounts + transactions)
 *        -> TransactionMapper (ExternalTransaction -> ParsedTransaction)
 *        -> StatementProcessingService.ingestOpenBankingTransactions(...)  [existing persistence]
 *        -> RecurringDetectionService  [existing detection, invoked inside ingest]
 * </pre>
 *
 * There is no bank-specific detection logic here - Open Banking simply feeds the same seam
 * the CSV flow uses.
 */
@Service
public class BankSyncService {

    private static final Logger log = LoggerFactory.getLogger(BankSyncService.class);
    private static final int LOOKBACK_DAYS = 90;

    private final BankProviderRegistry providerRegistry;
    private final TransactionMapper transactionMapper;
    private final StatementProcessingService statementProcessingService;

    public BankSyncService(
            BankProviderRegistry providerRegistry,
            TransactionMapper transactionMapper,
            StatementProcessingService statementProcessingService
    ) {
        this.providerRegistry = providerRegistry;
        this.transactionMapper = transactionMapper;
        this.statementProcessingService = statementProcessingService;
    }

    public BankSyncResult sync(BankConnection connection) {
        BankProviderClient provider = providerRegistry.get(connection.getProvider());
        String externalConnectionId = connection.getExternalConnectionId();

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(LOOKBACK_DAYS);

        List<ExternalAccount> accounts = provider.getAccounts(externalConnectionId);
        List<ParsedTransaction> mapped = new ArrayList<>();
        for (ExternalAccount account : accounts) {
            List<ExternalTransaction> transactions =
                    provider.getTransactions(account.externalAccountId(), from, to);
            for (ExternalTransaction transaction : transactions) {
                mapped.add(transactionMapper.toParsedTransaction(transaction));
            }
        }

        int subscriptionsDetected = statementProcessingService.ingestOpenBankingTransactions(mapped);
        log.info("Synced connection {} ({} account(s), {} transaction(s), {} subscription(s))",
                connection.getId(), accounts.size(), mapped.size(), subscriptionsDetected);

        return new BankSyncResult(accounts.size(), mapped.size(), subscriptionsDetected);
    }

    /**
     * Outcome of a sync run.
     *
     * @param accountsSynced        number of accounts fetched from the provider
     * @param transactionsImported  number of transactions ingested into the core
     * @param subscriptionsDetected total active subscriptions after detection re-ran
     */
    public record BankSyncResult(
            int accountsSynced,
            int transactionsImported,
            int subscriptionsDetected
    ) {
    }
}
