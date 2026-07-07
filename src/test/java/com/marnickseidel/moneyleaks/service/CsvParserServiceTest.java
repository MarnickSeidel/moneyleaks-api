package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserServiceTest {

    private final CsvParserService parser = new CsvParserService(new MerchantNormalizer());

    @Test
    void parsesSimpleCsv() throws Exception {
        String csv = """
                date,description,amount
                2025-01-05,NETFLIX.COM,-15.99
                2025-02-05,NETFLIX.COM,-15.99
                """;

        List<CsvParserService.ParsedTransaction> parsed = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(2, parsed.size());
        assertEquals(new BigDecimal("-15.99"), parsed.getFirst().amount());
        assertEquals("NETFLIX", parsed.getFirst().merchantNormalized());
    }
}
