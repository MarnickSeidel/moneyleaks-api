package com.marnickseidel.moneyleaks.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class MerchantNormalizer {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String description) {
        if (description == null || description.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = description.toUpperCase(Locale.ROOT);
        normalized = normalized.replace("INC.", " ");
        normalized = normalized.replace("B.V.", " ");
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();

        if (normalized.contains("NETFLIX")) {
            return "NETFLIX";
        }
        if (normalized.contains("DISNEY")) {
            return "DISNEY PLUS";
        }
        if (normalized.contains("SPOTIFY")) {
            return "SPOTIFY";
        }
        if (normalized.contains("ZIGGO")) {
            return "ZIGGO";
        }
        if (normalized.contains("BASIC FIT")) {
            return "BASIC FIT";
        }

        if (normalized.length() > 48) {
            return normalized.substring(0, 48).trim();
        }
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }
}
