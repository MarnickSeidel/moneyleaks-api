package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.config.RecurringDetectionProperties;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
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
import java.util.stream.Collectors;

@Service
public class RecurringDetectionService {

    private final RecurringDetectionProperties properties;

    public RecurringDetectionService(RecurringDetectionProperties properties) {
        this.properties = properties;
    }

    public List<DetectedSubscription> detect(List<ParsedTransaction> transactions) {
        Map<String, List<ParsedTransaction>> byMerchant = transactions.stream()
                .filter(tx -> tx.amount().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.groupingBy(
                        ParsedTransaction::merchantNormalized,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DetectedSubscription> detected = new ArrayList<>();

        for (List<ParsedTransaction> merchantTransactions : byMerchant.values()) {
            merchantTransactions.sort(Comparator.comparing(ParsedTransaction::transactionDate));

            for (List<ParsedTransaction> cluster : clusterByAmount(merchantTransactions)) {
                if (cluster.size() < properties.getMinOccurrences()) {
                    continue;
                }

                SubscriptionInterval interval = detectInterval(cluster);
                if (interval == SubscriptionInterval.UNKNOWN) {
                    continue;
                }

                detected.add(new DetectedSubscription(
                        cluster.getFirst().merchantNormalized(),
                        averageAmount(cluster),
                        interval,
                        cluster.size(),
                        cluster.getFirst().transactionDate(),
                        cluster.getLast().transactionDate(),
                        calculateConfidence(cluster, interval)
                ));
            }
        }

        return detected;
    }

    private List<List<ParsedTransaction>> clusterByAmount(List<ParsedTransaction> transactions) {
        List<List<ParsedTransaction>> clusters = new ArrayList<>();

        for (ParsedTransaction transaction : transactions) {
            BigDecimal absAmount = transaction.amount().abs();
            List<ParsedTransaction> matchedCluster = null;

            for (List<ParsedTransaction> cluster : clusters) {
                if (amountsMatch(absAmount, cluster.getFirst().amount().abs())) {
                    matchedCluster = cluster;
                    break;
                }
            }

            if (matchedCluster == null) {
                matchedCluster = new ArrayList<>();
                clusters.add(matchedCluster);
            }
            matchedCluster.add(transaction);
        }

        return clusters;
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

    private BigDecimal averageAmount(List<ParsedTransaction> cluster) {
        BigDecimal total = cluster.stream()
                .map(ParsedTransaction::amount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(cluster.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateConfidence(List<ParsedTransaction> cluster, SubscriptionInterval interval) {
        double intervalScore = interval == SubscriptionInterval.UNKNOWN ? 0.3 : 1.0;
        double occurrenceScore = Math.min(1.0, cluster.size() / 4.0);
        double confidence = (intervalScore * 0.6) + (occurrenceScore * 0.4);
        return BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    }

    public record DetectedSubscription(
            String merchantNormalized,
            BigDecimal amount,
            SubscriptionInterval intervalType,
            int occurrenceCount,
            LocalDate firstSeen,
            LocalDate lastSeen,
            BigDecimal confidence
    ) {
    }
}
