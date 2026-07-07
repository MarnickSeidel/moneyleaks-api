package com.marnickseidel.moneyleaks.util;

import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class PaymentMethodClassifier {

    /** ASN global transaction codes that indicate SEPA direct debit. */
    private static final Set<String> ASN_DIRECT_DEBIT_CODES = Set.of("EIC", "IC");

    /** ASN global transaction codes that indicate card/POS payments. */
    private static final Set<String> ASN_CARD_CODES = Set.of("BA", "BAA", "GE");

    public PaymentMethod classify(String description, String transactionType, String asnGlobalCode) {
        String desc = normalize(description);
        String type = normalize(transactionType);
        String asnCode = asnGlobalCode == null ? "" : asnGlobalCode.trim().toUpperCase(Locale.ROOT);

        if (!asnCode.isEmpty()) {
            if (ASN_DIRECT_DEBIT_CODES.contains(asnCode)) {
                return PaymentMethod.DIRECT_DEBIT;
            }
            if (ASN_CARD_CODES.contains(asnCode)) {
                return PaymentMethod.CARD_POS;
            }
        }

        if (containsAny(desc, "EUROPESE INCASSO", "SEPA INCASSO", "SEPA DIRECT DEBIT")
                || containsAny(type, "INCASSO", "SEPA DIRECT DEBIT", "DIRECT DEBIT")) {
            return PaymentMethod.DIRECT_DEBIT;
        }

        if (containsAny(desc, "VIA MULTISAFE", "VIA MOLLIE", "IDEAL", "PAYPAL")
                || containsAny(type, "IDEAL", "ONLINE PAYMENT")) {
            return PaymentMethod.ONLINE_PAYMENT;
        }

        if (containsAny(desc, "BETAALPAS", "BETAALAUTOMAAT", "PAYMENT TERMINAL", "PINBETALING", "CONTACTLESS")
                || containsAny(type, "PAYMENT TERMINAL", "BETAALPAS", "PIN", "POS")) {
            return PaymentMethod.CARD_POS;
        }

        if (containsAny(type, "OVERSCHRIJVING", "TRANSFER", "BANK TRANSFER")) {
            return PaymentMethod.TRANSFER;
        }

        return PaymentMethod.UNKNOWN;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
