package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.config.RecurringDetectionProperties;
import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.NonSubscriptionMerchantFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurringDetectionServiceTest {

    private RecurringDetectionService service;
    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @BeforeEach
    void setUp() {
        service = new RecurringDetectionService(
                new RecurringDetectionProperties(),
                new NonSubscriptionMerchantFilter(),
                normalizer
        );
    }

    @Test
    void detectsMonthlySubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2025-01-05", "NETFLIX.COM", "-15.99", PaymentMethod.DIRECT_DEBIT),
                tx("2025-02-05", "NETFLIX.COM", "-15.99", PaymentMethod.DIRECT_DEBIT),
                tx("2025-03-05", "NETFLIX.COM", "-15.99", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("NETFLIX", detected.getFirst().merchantNormalized());
        assertEquals(SubscriptionInterval.MONTHLY, detected.getFirst().intervalType());
    }

    @Test
    void ignoresIncomingPayments() {
        List<ParsedTransaction> transactions = List.of(
                tx("2025-01-05", "SALARY", "2500.00", PaymentMethod.TRANSFER),
                tx("2025-02-05", "SALARY", "2500.00", PaymentMethod.TRANSFER)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void detectsSepaHealthInsurerAcrossVaryingReferenceNumbers() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-28", "Europese incasso: NL-kenmerk 6001704262919083 Rel.nr. 475036190 "
                        + "Periode 01-05-2026/31-05-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-27", "Europese incasso: NL-kenmerk 2001505266034063 Rel.nr. 475036190 "
                        + "Periode 01-06-2026/30-06-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-kenmerk 1001906268995283 Rel.nr. 475036190 "
                        + "Periode 01-07-2026/31-07-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("CZ ZORGVERZEKERING", detected.getFirst().merchantNormalized());
        assertEquals(SubscriptionInterval.MONTHLY, detected.getFirst().intervalType());
        assertEquals(3, detected.getFirst().occurrenceCount());
    }

    @Test
    void detectsTwoParallelPoliciesBilledSameDayWithCloseAmounts() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-28", "Europese incasso: NL-kenmerk 111 Rel.nr. 475036190 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-04-28", "Europese incasso: NL-kenmerk 222 Rel.nr. 475036964 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-27", "Europese incasso: NL-kenmerk 333 Rel.nr. 475036190 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-27", "Europese incasso: NL-kenmerk 444 Rel.nr. 475036964 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-kenmerk 555 Rel.nr. 475036190 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-kenmerk 666 Rel.nr. 475036964 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(2, detected.size());
        assertTrue(detected.stream().allMatch(d -> d.merchantNormalized().equals("CZ ZORGVERZEKERING")));
        assertTrue(detected.stream().allMatch(d -> d.intervalType() == SubscriptionInterval.MONTHLY));
        assertTrue(detected.stream().anyMatch(d -> d.amount().compareTo(new BigDecimal("142.45")) == 0));
        assertTrue(detected.stream().anyMatch(d -> d.amount().compareTo(new BigDecimal("146.45")) == 0));
    }

    @Test
    void detectsVariableAmountTelecomSubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-29", "Europese incasso: NL-Klant Nr 510510112 April zie vodafone.nl/my", "-15.75", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-29", "Europese incasso: NL-Klant Nr 510510112 Mei zie vodafone.nl/my", "-20.75", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-Klant Nr 510510112 Juni zie vodafone.nl/my", "-15.75", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("VODAFONE", detected.getFirst().merchantNormalized());
        assertEquals(SubscriptionInterval.MONTHLY, detected.getFirst().intervalType());
        assertEquals(3, detected.getFirst().occurrenceCount());
    }

    @Test
    void doesNotFlagWildlyVaryingAmountsAsSubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-10", "ALBERT HEIJN", "-12.50", PaymentMethod.CARD_POS),
                tx("2026-05-10", "ALBERT HEIJN", "-98.30", PaymentMethod.CARD_POS),
                tx("2026-06-10", "ALBERT HEIJN", "-7.15", PaymentMethod.CARD_POS)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void doesNotFlagIrregularCadenceAsVariableSubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-03", "Europese incasso: NL-Klant Nr 999 zie vodafone.nl/my", "-15.00", PaymentMethod.DIRECT_DEBIT),
                tx("2026-04-20", "Europese incasso: NL-Klant Nr 999 zie vodafone.nl/my", "-16.00", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-Klant Nr 999 zie vodafone.nl/my", "-15.50", PaymentMethod.DIRECT_DEBIT)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void doesNotFlagCardPaymentsAtSupermarketEvenWithMonthlyCadence() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-05-17", "ALDI GNG009 GRONINGEN GRONINGEN", "-34.58", PaymentMethod.CARD_POS),
                tx("2026-06-20", "ALDI GNG009 GRONINGEN GRONINGEN", "-34.81", PaymentMethod.CARD_POS)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void doesNotFlagOnlineCheckoutAsSubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-05-25", "WATCHBANDJES SHOP NL VIA MULTISAFEPAY", "-14.95", PaymentMethod.ONLINE_PAYMENT),
                tx("2026-06-27", "WATCHBANDJES SHOP NL VIA MULTISAFEPAY", "-14.95", PaymentMethod.ONLINE_PAYMENT)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void detectsZiggoDirectDebitAcrossThreeMonths() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-23", "Ziggo Services B.V.", "-56.25", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-21", "Ziggo Services B.V.", "-56.25", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-24", "Ziggo Services B.V.", "-58.10", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("ZIGGO", detected.getFirst().merchantNormalized());
        assertEquals(SubscriptionInterval.MONTHLY, detected.getFirst().intervalType());
        assertEquals(3, detected.getFirst().occurrenceCount());
    }

    @Test
    void detectsSingleOccurrenceKnownHealthInsurerOnShortStatement() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-06-29", "Europese incasso: NL-kenmerk 1001906268995283 Rel.nr. 475036190 "
                        + "Periode 01-07-2026/31-07-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("CZ ZORGVERZEKERING", detected.getFirst().merchantNormalized());
        assertEquals(1, detected.getFirst().occurrenceCount());
        assertEquals(new BigDecimal("0.500"), detected.getFirst().confidence());
    }

    @Test
    void detectsSingleOccurrenceVodafoneOnShortStatement() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-06-29", "Europese incasso: NL-Klant Nr 510510112/402411481 Juni Factuur Nr "
                        + "320194517223 zie vodafone.nl/my", "-15.75", PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertEquals(1, detected.size());
        assertEquals("VODAFONE", detected.getFirst().merchantNormalized());
        assertEquals(new BigDecimal("0.500"), detected.getFirst().confidence());
    }

    @Test
    void doesNotFlagSingleOccurrenceRetailSepaAsSubscription() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-06-20", "Europese incasso: NL-kenmerk 111 WAT DE VIS NL51REVO356", "-85.00",
                        PaymentMethod.DIRECT_DEBIT)
        );

        assertTrue(service.detect(transactions).isEmpty());
    }

    @Test
    void detectsFullAsnStatementSubscriptions() {
        List<ParsedTransaction> transactions = List.of(
                tx("2026-04-28", "Europese incasso: NL-kenmerk 6001704262919083 Rel.nr. 475036190 "
                        + "Periode 01-05-2026/31-05-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-04-28", "Europese incasso: NL-kenmerk 3041704262919099 Rel.nr. 475036964 "
                        + "Periode 01-05-2026/31-05-2026 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-27", "Europese incasso: NL-kenmerk 2001505266034063 Rel.nr. 475036190 "
                        + "Periode 01-06-2026/30-06-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-27", "Europese incasso: NL-kenmerk 0041505266034083 Rel.nr. 475036964 "
                        + "Periode 01-06-2026/30-06-2026 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-kenmerk 9041906268995298 Rel.nr. 475036964 "
                        + "Periode 01-07-2026/31-07-2026 CZZorgverzekering", "-146.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-kenmerk 1001906268995283 Rel.nr. 475036190 "
                        + "Periode 01-07-2026/31-07-2026 CZZorgverzekering", "-142.45", PaymentMethod.DIRECT_DEBIT),
                tx("2026-04-29", "Europese incasso: NL-Klant Nr 510510112 April zie vodafone.nl/my", "-15.75",
                        PaymentMethod.DIRECT_DEBIT),
                tx("2026-05-29", "Europese incasso: NL-Klant Nr 510510112 Mei zie vodafone.nl/my", "-20.75",
                        PaymentMethod.DIRECT_DEBIT),
                tx("2026-06-29", "Europese incasso: NL-Klant Nr 510510112 Juni zie vodafone.nl/my", "-15.75",
                        PaymentMethod.DIRECT_DEBIT)
        );

        List<RecurringDetectionService.DetectedSubscription> detected = service.detect(transactions);

        assertTrue(detected.stream().anyMatch(d -> d.merchantNormalized().equals("CZ ZORGVERZEKERING")
                && d.amount().compareTo(new BigDecimal("142.45")) == 0));
        assertTrue(detected.stream().anyMatch(d -> d.merchantNormalized().equals("CZ ZORGVERZEKERING")
                && d.amount().compareTo(new BigDecimal("146.45")) == 0));
        assertTrue(detected.stream().anyMatch(d -> d.merchantNormalized().equals("VODAFONE")));
    }

    private ParsedTransaction tx(String date, String description, String amount, PaymentMethod paymentMethod) {
        return new ParsedTransaction(
                LocalDate.parse(date),
                description,
                new BigDecimal(amount),
                normalizer.normalize(description),
                paymentMethod,
                null,
                paymentMethod == PaymentMethod.DIRECT_DEBIT ? "NL98INGB0000845745" : null
        );
    }
}
