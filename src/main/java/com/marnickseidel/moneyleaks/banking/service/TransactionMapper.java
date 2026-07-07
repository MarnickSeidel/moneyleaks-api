package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.service.CsvParserService.ParsedTransaction;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Translates a provider-neutral {@link ExternalTransaction} into the same
 * {@link ParsedTransaction} shape the CSV parser emits. By producing the exact type the
 * core already understands - and reusing the existing {@link MerchantNormalizer} and
 * {@link PaymentMethodClassifier} - Open Banking data flows through normalisation and
 * detection identically to CSV data, with no separate logic.
 */
@Component
public class TransactionMapper {

    private final MerchantNormalizer merchantNormalizer;
    private final PaymentMethodClassifier paymentMethodClassifier;

    public TransactionMapper(
            MerchantNormalizer merchantNormalizer,
            PaymentMethodClassifier paymentMethodClassifier
    ) {
        this.merchantNormalizer = merchantNormalizer;
        this.paymentMethodClassifier = paymentMethodClassifier;
    }

    public ParsedTransaction toParsedTransaction(ExternalTransaction external) {
        String description = resolveDescription(external);
        String typeHint = toTransactionTypeHint(external.bankTransactionCode());
        PaymentMethod paymentMethod = paymentMethodClassifier.classify(description, typeHint, null);

        return new ParsedTransaction(
                external.bookingDate(),
                description,
                external.amount(),
                merchantNormalizer.normalize(description),
                paymentMethod,
                typeHint,
                external.counterpartyIban()
        );
    }

    private String resolveDescription(ExternalTransaction external) {
        if (external.description() != null && !external.description().isBlank()) {
            return external.description();
        }
        if (external.counterpartyName() != null && !external.counterpartyName().isBlank()) {
            return external.counterpartyName();
        }
        return "UNKNOWN";
    }

    /**
     * Maps a provider/ISO-20022 bank transaction code to a plain-text hint the existing
     * {@link PaymentMethodClassifier} recognises, so classification stays in one place.
     * Returns {@code null} when the code is unknown, leaving the classifier to fall back to
     * the description (and ultimately {@code UNKNOWN}, which stays eligible for detection).
     */
    private String toTransactionTypeHint(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.toUpperCase(Locale.ROOT);
        if (c.contains("DD") || c.contains("DDT") || c.contains("RDDT")
                || c.contains("DMCT") || c.contains("DIRECT DEBIT")) {
            return "SEPA direct debit";
        }
        if (c.contains("POS") || c.contains("CARD") || c.contains("CCRD") || c.contains("PMNT-CE")) {
            return "payment terminal";
        }
        if (c.contains("TRF") || c.contains("RCDT") || c.contains("ICDT") || c.contains("TRANSFER")) {
            return "transfer";
        }
        return null;
    }
}
