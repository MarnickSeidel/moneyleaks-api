package com.marnickseidel.moneyleaks.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MerchantNormalizer {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Reference/invoice/mandate numbers and period dates vary every cycle; inside SEPA
    // direct-debit text they carry no merchant identity, so drop every digit run.
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * SEPA direct-debit ("Europese incasso" / "SEPA incasso") descriptions wrap the real
     * creditor name in boilerplate plus per-period reference codes. Dropping these tokens
     * (after the numeric codes are stripped) leaves just the creditor name, so the same
     * subscription groups across months.
     */
    private static final Set<String> SEPA_STOPWORDS = Set.of(
            "EUROPESE", "INCASSO", "SEPA", "NL", "KENMERK", "MACHTIGING", "INCASSANT",
            "ID", "REL", "NR", "PERIODE", "KLANT", "FACTUUR", "ZZZ", "SDD", "ZIE",
            "MY", "BETR", "IBAN", "EUR", "TNV", "OMSCHRIJVING", "VOLGNR", "REFERENTIE",
            "JAN", "FEB", "MRT", "APR", "MEI", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEC"
    );

    private static final Set<String> KNOWN_SUBSCRIPTION_MERCHANTS = Set.of(
            "NETFLIX", "DISNEY PLUS", "SPOTIFY", "VODAFONE", "ZIGGO", "BASIC FIT",
            "ZILVEREN KRUIS", "MENZIS", "ONVZ", "VGZ", "CZ ZORGVERZEKERING", "ZORGVERZEKERING"
    );

    private static final Set<String> SUBSCRIPTION_KEYWORDS = Set.of(
            "VERZEKERING", "ZORG", "ENERGIE", "TELECOM", "ABONNEMENT", "HYPOTHEEK",
            "BELASTING", "NETFLIX", "SPOTIFY", "DISNEY", "VODAFONE", "ZIGGO"
    );

    public String normalize(String description) {
        if (description == null || description.isBlank()) {
            return "UNKNOWN";
        }

        String cleaned = description.toUpperCase(Locale.ROOT)
                .replace("INC.", " ")
                .replace("B.V.", " ");
        cleaned = NON_ALPHANUMERIC.matcher(cleaned).replaceAll(" ");
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        String known = matchKnownMerchant(cleaned);
        if (known != null) {
            return known;
        }

        if (cleaned.contains("EUROPESE INCASSO") || cleaned.contains("SEPA INCASSO")) {
            cleaned = DIGITS.matcher(cleaned).replaceAll(" ");
            cleaned = dropSepaStopwords(cleaned);
            cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
        }

        if (cleaned.isEmpty()) {
            return "UNKNOWN";
        }
        return cleaned.length() > 48 ? cleaned.substring(0, 48).trim() : cleaned;
    }

    private String matchKnownMerchant(String cleaned) {
        if (cleaned.contains("NETFLIX")) {
            return "NETFLIX";
        }
        if (cleaned.contains("DISNEY")) {
            return "DISNEY PLUS";
        }
        if (cleaned.contains("SPOTIFY")) {
            return "SPOTIFY";
        }
        if (cleaned.contains("VODAFONE")) {
            return "VODAFONE";
        }
        if (cleaned.contains("ZIGGO")) {
            return "ZIGGO";
        }
        if (cleaned.contains("BASIC FIT")) {
            return "BASIC FIT";
        }
        return matchHealthInsurer(cleaned);
    }

    /**
     * Dutch health insurers ("zorgverzekering") appear inside SEPA descriptions with the
     * insurer name glued to boilerplate (e.g. "CZZorgverzekering"). Map the common insurers
     * to a stable label so their monthly premiums group.
     */
    private String matchHealthInsurer(String cleaned) {
        if (cleaned.contains("ZILVEREN KRUIS")) {
            return "ZILVEREN KRUIS";
        }
        if (cleaned.contains("MENZIS")) {
            return "MENZIS";
        }
        if (cleaned.contains("ONVZ")) {
            return "ONVZ";
        }
        if (cleaned.contains("VGZ")) {
            return "VGZ";
        }
        if (cleaned.contains("CZ") && cleaned.contains("ZORGVERZEKERING")) {
            return "CZ ZORGVERZEKERING";
        }
        if (cleaned.contains("ZORGVERZEKERING") || cleaned.contains("ZORGVERZEKERAAR")) {
            return "ZORGVERZEKERING";
        }
        return null;
    }

    private String dropSepaStopwords(String cleaned) {
        return WHITESPACE.splitAsStream(cleaned)
                .filter(token -> !token.isBlank() && !SEPA_STOPWORDS.contains(token))
                .collect(Collectors.joining(" "));
    }

    public boolean isKnownSubscriptionMerchant(String merchantNormalized) {
        return merchantNormalized != null && KNOWN_SUBSCRIPTION_MERCHANTS.contains(merchantNormalized);
    }

    public boolean isSepaDirectDebit(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        String upper = description.toUpperCase(Locale.ROOT);
        return upper.contains("EUROPESE INCASSO") || upper.contains("SEPA INCASSO");
    }

    /**
     * Single-occurrence fallback: only flag when the merchant is a known subscription
     * provider, or a SEPA direct debit whose normalized creditor name clearly signals
     * insurance/telecom/energy (not one-off retail payments).
     */
    public boolean hasStrongSubscriptionSignal(String description, String merchantNormalized) {
        if (merchantNormalized == null || merchantNormalized.isBlank() || "UNKNOWN".equals(merchantNormalized)) {
            return false;
        }
        if (isKnownSubscriptionMerchant(merchantNormalized)) {
            return true;
        }
        if (!isSepaDirectDebit(description)) {
            return false;
        }
        String upper = merchantNormalized.toUpperCase(Locale.ROOT);
        return SUBSCRIPTION_KEYWORDS.stream().anyMatch(upper::contains);
    }
}
