package com.marnickseidel.moneyleaks.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Retailers and other merchants that may show a roughly monthly spend pattern but are not
 * subscriptions (supermarkets, etc.).
 */
@Component
public class NonSubscriptionMerchantFilter {

    private static final Set<String> SUPERMARKET_PREFIXES = Set.of(
            "ALDI",
            "LIDL",
            "JUMBO",
            "ALBERT HEIJN",
            "AH ",
            "AH TO GO",
            "PLUS ",
            "DIRK",
            "COOP",
            "SPAR",
            "ACTION",
            "KRUIDVAT",
            "ETOS",
            "HEMA"
    );

    public boolean isDeniedRetailMerchant(String merchantNormalized) {
        if (merchantNormalized == null || merchantNormalized.isBlank()) {
            return false;
        }
        String upper = merchantNormalized.toUpperCase(Locale.ROOT);
        for (String prefix : SUPERMARKET_PREFIXES) {
            if (upper.startsWith(prefix) || upper.contains(" " + prefix)) {
                return true;
            }
        }
        return false;
    }
}
