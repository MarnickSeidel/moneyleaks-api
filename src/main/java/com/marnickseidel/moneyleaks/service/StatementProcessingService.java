package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.model.dto.StatementProcessResponse;
import com.marnickseidel.moneyleaks.model.entity.BankTransaction;
import com.marnickseidel.moneyleaks.model.entity.Statement;
import com.marnickseidel.moneyleaks.model.entity.Subscription;
import com.marnickseidel.moneyleaks.model.enums.StatementStatus;
import com.marnickseidel.moneyleaks.model.enums.TransactionSource;
import com.marnickseidel.moneyleaks.repository.BankTransactionRepository;
import com.marnickseidel.moneyleaks.repository.StatementRepository;
import com.marnickseidel.moneyleaks.repository.SubscriptionRepository;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.service.RecurringDetectionService.DetectedSubscription;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatementProcessingService {

    private final StatementRepository statementRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CsvParserService csvParserService;
    private final RecurringDetectionService recurringDetectionService;

    public StatementProcessingService(
            StatementRepository statementRepository,
            BankTransactionRepository bankTransactionRepository,
            SubscriptionRepository subscriptionRepository,
            CsvParserService csvParserService,
            RecurringDetectionService recurringDetectionService
    ) {
        this.statementRepository = statementRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.csvParserService = csvParserService;
        this.recurringDetectionService = recurringDetectionService;
    }

    @Transactional
    public StatementProcessResponse process(Long statementId) throws IOException {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new StatementNotFoundException(statementId));

        statement.setStatus(StatementStatus.PROCESSING);
        statementRepository.save(statement);

        try {
            bankTransactionRepository.deleteByStatementId(statementId);

            List<ParsedTransaction> parsed = csvParserService.parse(
                    new ByteArrayInputStream(statement.getContent())
            );

            Instant now = Instant.now();
            for (ParsedTransaction tx : parsed) {
                persistTransaction(tx, statement, TransactionSource.CSV_UPLOAD, now);
            }

            statement.setStatus(StatementStatus.PROCESSED);
            statement.setProcessedAt(now);
            statementRepository.save(statement);

            List<DetectedSubscription> detected = rebuildSubscriptions(now);

            return new StatementProcessResponse(
                    statementId,
                    StatementStatus.PROCESSED,
                    parsed.size(),
                    detected.size(),
                    now
            );
        } catch (RuntimeException | IOException ex) {
            statement.setStatus(StatementStatus.FAILED);
            statementRepository.save(statement);
            throw ex;
        }
    }

    /**
     * Shared ingestion seam for Open Banking. Transactions arrive already normalised (as
     * {@link ParsedTransaction}, exactly like the CSV parser produces) and are persisted and
     * fed into the very same detection pipeline the CSV flow uses. This is the single point
     * where non-CSV sources join the core; there is no second detection engine.
     *
     * <p>Re-sync is idempotent: the previous Open Banking snapshot is replaced, mirroring how
     * CSV re-processing clears a statement's rows first. CSV transactions are never touched.
     *
     * @return the number of subscriptions detected across all sources after ingestion
     */
    @Transactional
    public int ingestOpenBankingTransactions(List<ParsedTransaction> transactions) {
        Instant now = Instant.now();
        bankTransactionRepository.deleteBySource(TransactionSource.OPEN_BANKING);
        for (ParsedTransaction tx : transactions) {
            persistTransaction(tx, null, TransactionSource.OPEN_BANKING, now);
        }
        return rebuildSubscriptions(now).size();
    }

    private void persistTransaction(
            ParsedTransaction tx, Statement statement, TransactionSource source, Instant now) {
        BankTransaction entity = new BankTransaction();
        entity.setStatement(statement);
        entity.setTransactionDate(tx.transactionDate());
        entity.setDescription(tx.description());
        entity.setAmount(tx.amount());
        entity.setMerchantNormalized(tx.merchantNormalized());
        entity.setPaymentMethod(tx.paymentMethod());
        entity.setTransactionType(tx.transactionType());
        entity.setCounterpartyIban(tx.counterpartyIban());
        entity.setSource(source);
        entity.setCreatedAt(now);
        bankTransactionRepository.save(entity);
    }

    /**
     * Re-run detection across every imported transaction so subscriptions from multiple
     * statements stay consistent and stale false positives are removed.
     */
    private List<DetectedSubscription> rebuildSubscriptions(Instant now) {
        subscriptionRepository.deactivateAll();

        List<ParsedTransaction> allTransactions = new ArrayList<>();
        for (BankTransaction entity : bankTransactionRepository.findAllByOrderByTransactionDateAsc()) {
            allTransactions.add(new ParsedTransaction(
                    entity.getTransactionDate(),
                    entity.getDescription(),
                    entity.getAmount(),
                    entity.getMerchantNormalized(),
                    entity.getPaymentMethod(),
                    entity.getTransactionType(),
                    entity.getCounterpartyIban()
            ));
        }

        List<DetectedSubscription> detected = recurringDetectionService.detect(allTransactions);
        upsertSubscriptions(detected, now);
        return detected;
    }

    private void upsertSubscriptions(List<DetectedSubscription> detected, Instant now) {
        for (DetectedSubscription item : detected) {
            Subscription subscription = subscriptionRepository
                    .findByMerchantNormalizedAndAmountAndIntervalType(
                            item.merchantNormalized(),
                            item.amount(),
                            item.intervalType()
                    )
                    .orElseGet(Subscription::new);

            subscription.setMerchantNormalized(item.merchantNormalized());
            subscription.setAmount(item.amount());
            subscription.setCurrency("EUR");
            subscription.setIntervalType(item.intervalType());
            subscription.setOccurrenceCount(item.occurrenceCount());
            subscription.setFirstSeen(item.firstSeen());
            subscription.setLastSeen(item.lastSeen());
            subscription.setConfidence(item.confidence());
            subscription.setSampleDescription(item.sampleDescription());
            subscription.setSourceIban(item.sourceIban());
            subscription.setPaymentMethod(item.paymentMethod());
            subscription.setActive(true);

            if (subscription.getCreatedAt() == null) {
                subscription.setCreatedAt(now);
            }
            subscription.setUpdatedAt(now);
            subscriptionRepository.save(subscription);
        }
    }
}
