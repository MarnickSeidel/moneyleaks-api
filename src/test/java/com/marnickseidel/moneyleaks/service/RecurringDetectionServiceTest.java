package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.config.RecurringDetectionProperties;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurringDetectionServiceTest {

    private RecurringDetectionService service;

    @BeforeEach
    void setUp() {
        service = new RecurringDetectionService(new RecurringDetectionProperties());
    }

    @Test
    void detectsMonthlySubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2025-01-05", "NETFLIX.COM", "-15.99"),
                tx("2025-02-05", "NETFLIX.COM", "-15.99"),
                tx("2025-03-05", "NETFLIX.COM", "-15.99")
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("NETFLIX", detected.getFirst().merchantNormalized());
        assertEquals(SubscriptionInterval.MONTHLY, detected.getFirst().intervalType());
    }

    @Test
    void ignoresIncomingPayments() {
        List<ParsedTransaction> transactions = List.of(
                tx("2025-01-05", "SALARY", "2500.00"),
                tx("2025-02-05", "SALARY", "2500.00")
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    private ParsedTransaction tx(String date, String description, String amount) {
        MerchantNormalizer normalizer = new MerchantNormalizer();
        return new ParsedTransaction(
                LocalDate.parse(date),
                description,
                new BigDecimal(amount),
                normalizer.normalize(description)
        );
    }
}
