package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.config.RecurringDetectionProperties;
import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.NonSubscriptionMerchantFilter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecurringDetectionService {

    private static final double AMOUNT_SIMILARITY_FACTOR = 2.0;
    private static final double VARIABLE_AMOUNT_CONFIDENCE_PENALTY = 0.85;
    private static final double SINGLE_OCCURRENCE_CONFIDENCE = 0.50;

    private final RecurringDetectionProperties properties;
    private final NonSubscriptionMerchantFilter nonSubscriptionMerchantFilter;
    private final MerchantNormalizer merchantNormalizer;

    public RecurringDetectionService(
            RecurringDetectionProperties properties,
            NonSubscriptionMerchantFilter nonSubscriptionMerchantFilter,
            MerchantNormalizer merchantNormalizer
    ) {
        this.properties = properties;
        this.nonSubscriptionMerchantFilter = nonSubscriptionMerchantFilter;
        this.merchantNormalizer = merchantNormalizer;
    }

    public List<DetectedSubscription> detect(List<ParsedTransaction> transactions) {
        Map<String, List<ParsedTransaction>> byMerchant = transactions.stream()
                .filter(this::isEligibleOutgoingTransaction)
                .collect(Collectors.groupingBy(
                        ParsedTransaction::merchantNormalized,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DetectedSubscription> detected = new ArrayList<>();

        for (List<ParsedTransaction> merchantTransactions : byMerchant.values()) {
            merchantTransactions.sort(Comparator.comparing(ParsedTransaction::transactionDate));

            boolean detectedForMerchant = false;
            for (List<ParsedTransaction> cluster : clusterByAmount(merchantTransactions)) {
                if (cluster.size() < properties.getMinOccurrences()) {
                    continue;
                }

                SubscriptionInterval interval = detectInterval(cluster);
                if (interval == SubscriptionInterval.UNKNOWN) {
                    continue;
                }

                detected.add(toDetectedSubscription(cluster, interval, false));
                detectedForMerchant = true;
            }

            if (!detectedForMerchant) {
                Optional<DetectedSubscription> variable = detectVariableAmountSubscription(merchantTransactions);
                if (variable.isPresent()) {
                    detected.add(variable.get());
                    detectedForMerchant = true;
                }
            }

            if (!detectedForMerchant) {
                detectSingleOccurrenceSubscription(merchantTransactions).ifPresent(detected::add);
            }
        }

        return detected;
    }

    private boolean isEligibleOutgoingTransaction(ParsedTransaction transaction) {
        if (transaction.amount().compareTo(BigDecimal.ZERO) >= 0) {
            return false;
        }
        if (nonSubscriptionMerchantFilter.isDeniedRetailMerchant(transaction.merchantNormalized())) {
            return false;
        }
        return isSubscriptionLikePayment(transaction.paymentMethod());
    }

    /**
     * Card/POS and one-off online checkouts are usually groceries or web shops, not recurring bills.
     * Direct debits and unknown methods (simple CSV) remain eligible.
     */
    private boolean isSubscriptionLikePayment(PaymentMethod paymentMethod) {
        return paymentMethod != PaymentMethod.CARD_POS && paymentMethod != PaymentMethod.ONLINE_PAYMENT;
    }

    private Optional<DetectedSubscription> detectVariableAmountSubscription(
            List<ParsedTransaction> merchantTransactions) {
        if (merchantTransactions.size() < properties.getMinOccurrences()) {
            return Optional.empty();
        }

        SubscriptionInterval interval = detectRegularInterval(merchantTransactions);
        if (interval == SubscriptionInterval.UNKNOWN || !amountsWithinFactor(merchantTransactions)) {
            return Optional.empty();
        }

        return Optional.of(toDetectedSubscription(merchantTransactions, interval, true));
    }

    /**
     * Short statement windows may contain only one charge for a true subscription. Surface
     * known providers and SEPA insurance/telecom debits at reduced confidence.
     */
    private Optional<DetectedSubscription> detectSingleOccurrenceSubscription(
            List<ParsedTransaction> merchantTransactions) {
        if (merchantTransactions.size() != 1) {
            return Optional.empty();
        }

        ParsedTransaction transaction = merchantTransactions.getFirst();
        if (!merchantNormalizer.hasStrongSubscriptionSignal(
                transaction.description(), transaction.merchantNormalized())) {
            return Optional.empty();
        }

        return Optional.of(new DetectedSubscription(
                transaction.merchantNormalized(),
                transaction.amount().abs(),
                SubscriptionInterval.MONTHLY,
                1,
                transaction.transactionDate(),
                transaction.transactionDate(),
                BigDecimal.valueOf(SINGLE_OCCURRENCE_CONFIDENCE).setScale(3, RoundingMode.HALF_UP),
                transaction.description(),
                transaction.counterpartyIban(),
                transaction.paymentMethod()
        ));
    }

    private DetectedSubscription toDetectedSubscription(
            List<ParsedTransaction> cluster,
            SubscriptionInterval interval,
            boolean variableAmount) {
        ParsedTransaction representative = cluster.getLast();
        return new DetectedSubscription(
                cluster.getFirst().merchantNormalized(),
                averageAmount(cluster),
                interval,
                cluster.size(),
                cluster.getFirst().transactionDate(),
                cluster.getLast().transactionDate(),
                calculateConfidence(cluster, interval, variableAmount),
                representative.description(),
                representative.counterpartyIban(),
                representative.paymentMethod()
        );
    }

    private List<List<ParsedTransaction>> clusterByAmount(List<ParsedTransaction> transactions) {
        List<List<ParsedTransaction>> clusters = new ArrayList<>();

        for (ParsedTransaction transaction : transactions) {
            BigDecimal absAmount = transaction.amount().abs();
            List<ParsedTransaction> bestCluster = null;
            BigDecimal bestDelta = null;

            for (List<ParsedTransaction> cluster : clusters) {
                BigDecimal clusterAmount = cluster.getFirst().amount().abs();
                if (!amountsMatch(absAmount, clusterAmount) || containsDate(cluster, transaction.transactionDate())) {
                    continue;
                }
                BigDecimal delta = absAmount.subtract(clusterAmount).abs();
                if (bestDelta == null || delta.compareTo(bestDelta) < 0) {
                    bestDelta = delta;
                    bestCluster = cluster;
                }
            }

            if (bestCluster == null) {
                bestCluster = new ArrayList<>();
                clusters.add(bestCluster);
            }
            bestCluster.add(transaction);
        }

        return clusters;
    }

    private boolean containsDate(List<ParsedTransaction> cluster, LocalDate date) {
        return cluster.stream().anyMatch(tx -> tx.transactionDate().equals(date));
    }

    private boolean amountsMatch(BigDecimal left, BigDecimal right) {
        BigDecimal tolerance = right.multiply(BigDecimal.valueOf(properties.getAmountTolerancePercent()))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    private SubscriptionInterval detectInterval(List<ParsedTransaction> cluster) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < cluster.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(
                    cluster.get(i - 1).transactionDate(),
                    cluster.get(i).transactionDate()
            ));
        }

        long averageGap = Math.round(gaps.stream().mapToLong(Long::longValue).average().orElse(0));

        if (averageGap >= properties.getMonthlyMinDays() && averageGap <= properties.getMonthlyMaxDays()) {
            return SubscriptionInterval.MONTHLY;
        }
        if (averageGap >= properties.getYearlyMinDays() && averageGap <= properties.getYearlyMaxDays()) {
            return SubscriptionInterval.YEARLY;
        }
        return SubscriptionInterval.UNKNOWN;
    }

    private SubscriptionInterval detectRegularInterval(List<ParsedTransaction> cluster) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < cluster.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(
                    cluster.get(i - 1).transactionDate(),
                    cluster.get(i).transactionDate()
            ));
        }
        if (gaps.isEmpty()) {
            return SubscriptionInterval.UNKNOWN;
        }
        if (allGapsWithin(gaps, properties.getMonthlyMinDays(), properties.getMonthlyMaxDays())) {
            return SubscriptionInterval.MONTHLY;
        }
        if (allGapsWithin(gaps, properties.getYearlyMinDays(), properties.getYearlyMaxDays())) {
            return SubscriptionInterval.YEARLY;
        }
        return SubscriptionInterval.UNKNOWN;
    }

    private boolean allGapsWithin(List<Long> gaps, int minDays, int maxDays) {
        return gaps.stream().allMatch(gap -> gap >= minDays && gap <= maxDays);
    }

    private boolean amountsWithinFactor(List<ParsedTransaction> cluster) {
        BigDecimal min = null;
        BigDecimal max = null;
        for (ParsedTransaction tx : cluster) {
            BigDecimal abs = tx.amount().abs();
            if (min == null || abs.compareTo(min) < 0) {
                min = abs;
            }
            if (max == null || abs.compareTo(max) > 0) {
                max = abs;
            }
        }
        if (min == null || min.signum() == 0) {
            return false;
        }
        return max.compareTo(min.multiply(BigDecimal.valueOf(AMOUNT_SIMILARITY_FACTOR))) <= 0;
    }

    private BigDecimal averageAmount(List<ParsedTransaction> cluster) {
        BigDecimal total = cluster.stream()
                .map(ParsedTransaction::amount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(cluster.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateConfidence(
            List<ParsedTransaction> cluster, SubscriptionInterval interval, boolean variableAmount) {
        double intervalScore = interval == SubscriptionInterval.UNKNOWN ? 0.3 : 1.0;
        double occurrenceScore = Math.min(1.0, cluster.size() / 4.0);
        double confidence = (intervalScore * 0.6) + (occurrenceScore * 0.4);
        if (variableAmount) {
            confidence *= VARIABLE_AMOUNT_CONFIDENCE_PENALTY;
        }
        return BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    }

    public record DetectedSubscription(
            String merchantNormalized,
            BigDecimal amount,
            SubscriptionInterval intervalType,
            int occurrenceCount,
            LocalDate firstSeen,
            LocalDate lastSeen,
            BigDecimal confidence,
            String sampleDescription,
            String sourceIban,
            PaymentMethod paymentMethod
    ) {
    }
}
