package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CsvParserService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    // ASN Bank exports are headerless, comma-separated, with a fixed column layout.
    private static final int ASN_DATE_INDEX = 0;
    private static final int ASN_OWN_IBAN_INDEX = 1;
    private static final int ASN_COUNTERPARTY_IBAN_INDEX = 2;
    private static final int ASN_COUNTERPARTY_NAME_INDEX = 3;
    private static final int ASN_AMOUNT_INDEX = 10;
    private static final int ASN_GLOBAL_CODE_INDEX = 14;
    private static final int ASN_DESCRIPTION_INDEX = 17;
    private static final Pattern IBAN_PATTERN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]+");

    private final MerchantNormalizer merchantNormalizer;
    private final PaymentMethodClassifier paymentMethodClassifier;

    public CsvParserService(
            MerchantNormalizer merchantNormalizer,
            PaymentMethodClassifier paymentMethodClassifier
    ) {
        this.merchantNormalizer = merchantNormalizer;
        this.paymentMethodClassifier = paymentMethodClassifier;
    }

    public List<ParsedTransaction> parse(InputStream inputStream) throws IOException {
        List<ParsedTransaction> transactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            char delimiter = headerLine.contains(";") ? ';' : ',';
            List<String> firstFields = splitLine(headerLine, delimiter);

            if (looksLikeAsnRow(firstFields)) {
                transactions.add(parseAsnRow(firstFields, headerLine));

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    transactions.add(parseAsnRow(splitLine(line, delimiter), line));
                }
            } else {
                IngLayout ingLayout = detectIngLayout(firstFields);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    List<String> parts = splitLine(line, delimiter);

                    ParsedTransaction transaction = ingLayout != null
                            ? parseIngRow(parts, ingLayout, line)
                            : parseSimpleRow(parts, line);

                    transactions.add(transaction);
                }
            }
        }

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("No transactions found in CSV");
        }

        return transactions;
    }

    private ParsedTransaction parseSimpleRow(List<String> parts, String rawLine) {
        if (parts.size() < 3) {
            throw new IllegalArgumentException("Invalid CSV row: " + rawLine);
        }

        LocalDate date = parseDate(parts.get(0).trim());
        String description = parts.get(1).trim();
        BigDecimal amount = parseAmount(parts.get(2).trim());
        PaymentMethod paymentMethod = paymentMethodClassifier.classify(description, null, null);

        return new ParsedTransaction(
                date,
                description,
                amount,
                merchantNormalizer.normalize(description),
                paymentMethod,
                null,
                null
        );
    }

    private ParsedTransaction parseIngRow(List<String> parts, IngLayout layout, String rawLine) {
        int required = Math.max(layout.dateIndex(), Math.max(layout.descriptionIndex(),
                Math.max(layout.amountIndex(), layout.debitCreditIndex())));
        if (parts.size() <= required) {
            throw new IllegalArgumentException("Invalid ING CSV row: " + rawLine);
        }

        LocalDate date = parseDate(parts.get(layout.dateIndex()).trim());
        String description = parts.get(layout.descriptionIndex()).trim();
        BigDecimal amount = parseAmount(parts.get(layout.amountIndex()).trim()).abs();

        String direction = parts.get(layout.debitCreditIndex()).trim().toLowerCase(Locale.ROOT);
        if (direction.startsWith("af") || direction.startsWith("debit")) {
            amount = amount.negate();
        }

        String transactionType = layout.transactionTypeIndex() >= 0 && parts.size() > layout.transactionTypeIndex()
                ? parts.get(layout.transactionTypeIndex()).trim()
                : null;
        String counterpartyIban = layout.counterpartyIndex() >= 0 && parts.size() > layout.counterpartyIndex()
                ? blankToNull(parts.get(layout.counterpartyIndex()).trim())
                : null;
        PaymentMethod paymentMethod = paymentMethodClassifier.classify(description, transactionType, null);

        return new ParsedTransaction(
                date,
                description,
                amount,
                merchantNormalizer.normalize(description),
                paymentMethod,
                transactionType,
                counterpartyIban
        );
    }

    private boolean looksLikeAsnRow(List<String> parts) {
        if (parts.size() <= ASN_DESCRIPTION_INDEX) {
            return false;
        }
        if (!IBAN_PATTERN.matcher(parts.get(ASN_OWN_IBAN_INDEX).trim()).matches()) {
            return false;
        }
        try {
            parseDate(parts.get(ASN_DATE_INDEX).trim());
            parseAmount(parts.get(ASN_AMOUNT_INDEX).trim());
        } catch (RuntimeException ex) {
            return false;
        }
        return true;
    }

    private ParsedTransaction parseAsnRow(List<String> parts, String rawLine) {
        if (parts.size() <= ASN_DESCRIPTION_INDEX) {
            throw new IllegalArgumentException("Invalid ASN CSV row: " + rawLine);
        }

        LocalDate date = parseDate(parts.get(ASN_DATE_INDEX).trim());
        BigDecimal amount = parseAmount(parts.get(ASN_AMOUNT_INDEX).trim());

        String description = stripSingleQuotes(parts.get(ASN_DESCRIPTION_INDEX).trim());
        if (description.isEmpty()) {
            description = stripSingleQuotes(parts.get(ASN_COUNTERPARTY_NAME_INDEX).trim());
        }

        String counterpartyIban = blankToNull(parts.get(ASN_COUNTERPARTY_IBAN_INDEX).trim());
        String asnGlobalCode = parts.size() > ASN_GLOBAL_CODE_INDEX
                ? parts.get(ASN_GLOBAL_CODE_INDEX).trim()
                : null;
        PaymentMethod paymentMethod = paymentMethodClassifier.classify(description, null, asnGlobalCode);

        return new ParsedTransaction(
                date,
                description,
                amount,
                merchantNormalizer.normalize(description),
                paymentMethod,
                asnGlobalCode,
                counterpartyIban
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stripSingleQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private IngLayout detectIngLayout(List<String> headers) {
        int dateIndex = -1;
        int descriptionIndex = -1;
        int amountIndex = -1;
        int debitCreditIndex = -1;
        int transactionTypeIndex = -1;
        int counterpartyIndex = -1;

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).trim().toLowerCase(Locale.ROOT);
            if (dateIndex < 0 && (header.equals("date") || header.equals("datum"))) {
                dateIndex = i;
            } else if (descriptionIndex < 0
                    && (header.contains("name / description") || header.contains("naam / omschrijving"))) {
                descriptionIndex = i;
            } else if (amountIndex < 0 && (header.startsWith("amount") || header.startsWith("bedrag"))) {
                amountIndex = i;
            } else if (debitCreditIndex < 0
                    && (header.contains("debit/credit") || header.contains("af bij") || header.contains("af/bij"))) {
                debitCreditIndex = i;
            } else if (transactionTypeIndex < 0
                    && (header.contains("transaction type") || header.equals("mutatiesoort"))) {
                transactionTypeIndex = i;
            } else if (counterpartyIndex < 0
                    && (header.equals("counterparty") || header.equals("tegenrekening"))) {
                counterpartyIndex = i;
            }
        }

        if (dateIndex >= 0 && descriptionIndex >= 0 && amountIndex >= 0 && debitCreditIndex >= 0) {
            return new IngLayout(dateIndex, descriptionIndex, amountIndex, debitCreditIndex,
                    transactionTypeIndex, counterpartyIndex);
        }
        return null;
    }

    private List<String> splitLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("Unsupported date format: " + value);
    }

    private BigDecimal parseAmount(String value) {
        String normalized = value
                .replace("€", "")
                .replace("EUR", "")
                .replace(" ", "")
                .trim();

        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else {
            normalized = normalized.replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }

    private record IngLayout(
            int dateIndex,
            int descriptionIndex,
            int amountIndex,
            int debitCreditIndex,
            int transactionTypeIndex,
            int counterpartyIndex
    ) {
    }

    public record ParsedTransaction(
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            String merchantNormalized,
            PaymentMethod paymentMethod,
            String transactionType,
            String counterpartyIban
    ) {
    }
}
