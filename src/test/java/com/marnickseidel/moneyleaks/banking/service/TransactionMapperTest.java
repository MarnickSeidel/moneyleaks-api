package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper(
            new MerchantNormalizer(),
            new PaymentMethodClassifier()
    );

    @Test
    void mapsExternalTransactionReusingNormalizerAndClassifier() {
        ExternalTransaction external = new ExternalTransaction(
                "gc-tx-1",
                LocalDate.of(2026, 3, 5),
                new BigDecimal("-12.99"),
                "EUR",
                "NETFLIX.COM",
                "Netflix International B.V.",
                "LU810670006550194759",
                "DD"
        );

        ParsedTransaction parsed = mapper.toParsedTransaction(external);

        assertEquals(LocalDate.of(2026, 3, 5), parsed.transactionDate());
        assertEquals(new BigDecimal("-12.99"), parsed.amount());
        assertEquals("NETFLIX.COM", parsed.description());
        // Reuses the shared MerchantNormalizer.
        assertEquals("NETFLIX", parsed.merchantNormalized());
        // "DD" code -> direct-debit hint -> classifier resolves DIRECT_DEBIT.
        assertEquals(PaymentMethod.DIRECT_DEBIT, parsed.paymentMethod());
        assertEquals("LU810670006550194759", parsed.counterpartyIban());
    }

    @Test
    void keepsIncomingAmountsPositive() {
        ExternalTransaction salary = new ExternalTransaction(
                "gc-tx-2",
                LocalDate.of(2026, 3, 25),
                new BigDecimal("2500.00"),
                "EUR",
                "Salary payment",
                "Employer B.V.",
                null,
                "TRF"
        );

        ParsedTransaction parsed = mapper.toParsedTransaction(salary);

        assertEquals(new BigDecimal("2500.00"), parsed.amount());
        assertEquals(PaymentMethod.TRANSFER, parsed.paymentMethod());
        assertNull(parsed.counterpartyIban());
    }

    @Test
    void fallsBackToCounterpartyNameWhenDescriptionMissing() {
        ExternalTransaction external = new ExternalTransaction(
                "gc-tx-3",
                LocalDate.of(2026, 3, 18),
                new BigDecimal("-40.00"),
                "EUR",
                null,
                "Ziggo Services B.V.",
                null,
                null
        );

        ParsedTransaction parsed = mapper.toParsedTransaction(external);

        assertEquals("Ziggo Services B.V.", parsed.description());
        assertEquals("ZIGGO", parsed.merchantNormalized());
    }
}
