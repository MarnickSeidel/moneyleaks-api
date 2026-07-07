package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
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

@Service
public class CsvParserService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final MerchantNormalizer merchantNormalizer;

    public CsvParserService(MerchantNormalizer merchantNormalizer) {
        this.merchantNormalizer = merchantNormalizer;
    }

    public List<ParsedTransaction> parse(InputStream inputStream) throws IOException {
        List<ParsedTransaction> transactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            char delimiter = headerLine.contains(";") ? ';' : ',';
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(String.valueOf(delimiter), -1);
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Invalid CSV row: " + line);
                }

                LocalDate date = parseDate(parts[0].trim());
                String description = parts[1].trim();
                BigDecimal amount = parseAmount(parts[2].trim());

                transactions.add(new ParsedTransaction(
                        date,
                        description,
                        amount,
                        merchantNormalizer.normalize(description)
                ));
            }
        }

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("No transactions found in CSV");
        }

        return transactions;
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
                .replace(",", ".");

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid amount: " + value);
        }
    }

    public record ParsedTransaction(
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            String merchantNormalized
    ) {
    }
}
