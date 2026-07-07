package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.model.dto.StatementProcessResponse;
import com.marnickseidel.moneyleaks.model.entity.BankTransaction;
import com.marnickseidel.moneyleaks.model.entity.Statement;
import com.marnickseidel.moneyleaks.model.entity.Subscription;
import com.marnickseidel.moneyleaks.model.enums.StatementStatus;
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

        if (statement.getStatus() == StatementStatus.PROCESSED) {
            int existingTransactions = bankTransactionRepository.findByStatementId(statementId).size();
            return new StatementProcessResponse(
                    statementId,
                    statement.getStatus(),
                    existingTransactions,
                    subscriptionRepository.findByActiveTrueOrderByAmountDesc().size(),
                    statement.getProcessedAt()
            );
        }

        statement.setStatus(StatementStatus.PROCESSING);
        statementRepository.save(statement);

        try {
            bankTransactionRepository.deleteByStatementId(statementId);

            List<ParsedTransaction> parsed = csvParserService.parse(
                    new ByteArrayInputStream(statement.getContent())
            );

            Instant now = Instant.now();
            for (ParsedTransaction tx : parsed) {
                BankTransaction entity = new BankTransaction();
                entity.setStatement(statement);
                entity.setTransactionDate(tx.transactionDate());
                entity.setDescription(tx.description());
                entity.setAmount(tx.amount());
                entity.setMerchantNormalized(tx.merchantNormalized());
                entity.setCreatedAt(now);
                bankTransactionRepository.save(entity);
            }

            List<DetectedSubscription> detected = recurringDetectionService.detect(parsed);
            upsertSubscriptions(detected, now);

            statement.setStatus(StatementStatus.PROCESSED);
            statement.setProcessedAt(now);
            statementRepository.save(statement);

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
            subscription.setActive(true);

            if (subscription.getCreatedAt() == null) {
                subscription.setCreatedAt(now);
            }
            subscription.setUpdatedAt(now);
            subscriptionRepository.save(subscription);
        }
    }
}
