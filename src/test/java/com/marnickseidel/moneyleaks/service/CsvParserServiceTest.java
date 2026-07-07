package com.marnickseidel.moneyleaks.service;

import com.marnickseidel.moneyleaks.util.MerchantNormalizer;
import com.marnickseidel.moneyleaks.util.PaymentMethodClassifier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserServiceTest {

    private final CsvParserService parser = new CsvParserService(
            new MerchantNormalizer(),
            new PaymentMethodClassifier()
    );

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
        assertEquals(com.marnickseidel.moneyleaks.model.enums.PaymentMethod.UNKNOWN,
                parsed.getFirst().paymentMethod());
    }

    @Test
    void parsesIngEnglishExport() throws Exception {
        // Synthetic sample matching the real ING English CSV export layout (fake data).
        String csv = """
                "Date";"Name / Description";"Account";"Counterparty";"Code";"Debit/credit";"Amount (EUR)";"Transaction type";"Notifications";"Resulting balance";"Tag"
                "20260616";"NETFLIX INTERNATIONAL B.V.";"NL00INGB0000000000";"LU810670006550194759";"IC";"Debit";"9,99";"SEPA direct debit";"Name: NETFLIX; Reference: 123";"6280,31";""
                "20260605";"HAYS B.V.";"NL00INGB0000000000";"NL59KRED0633011312";"OV";"Credit";"1.329,36";"Transfer";"Salary payment";"13658,89";""
                """;

        List<CsvParserService.ParsedTransaction> parsed = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(2, parsed.size());

        CsvParserService.ParsedTransaction netflix = parsed.getFirst();
        assertEquals(LocalDate.of(2026, 6, 16), netflix.transactionDate());
        assertEquals(new BigDecimal("-9.99"), netflix.amount());
        assertEquals("NETFLIX", netflix.merchantNormalized());
        assertEquals("NETFLIX INTERNATIONAL B.V.", netflix.description());
        assertEquals(com.marnickseidel.moneyleaks.model.enums.PaymentMethod.DIRECT_DEBIT, netflix.paymentMethod());
        assertEquals("LU810670006550194759", netflix.counterpartyIban());

        CsvParserService.ParsedTransaction salary = parsed.get(1);
        assertEquals(LocalDate.of(2026, 6, 5), salary.transactionDate());
        assertEquals(new BigDecimal("1329.36"), salary.amount());
    }

    @Test
    void parsesIngDutchExport() throws Exception {
        // Synthetic sample matching the ING Dutch CSV export layout (fake data).
        String csv = """
                "Datum";"Naam / Omschrijving";"Rekening";"Tegenrekening";"Code";"Af Bij";"Bedrag (EUR)";"Mutatiesoort";"Mededelingen"
                "20260101";"SPOTIFY AB";"NL00INGB0000000000";"NL00INGB0000000001";"IC";"Af";"10,99";"Incasso";"Spotify Premium"
                "20260102";"WERKGEVER B.V.";"NL00INGB0000000000";"NL00INGB0000000002";"OV";"Bij";"2500,00";"Overschrijving";"Salaris"
                """;

        List<CsvParserService.ParsedTransaction> parsed = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(2, parsed.size());

        CsvParserService.ParsedTransaction spotify = parsed.getFirst();
        assertEquals(LocalDate.of(2026, 1, 1), spotify.transactionDate());
        assertEquals(new BigDecimal("-10.99"), spotify.amount());
        assertEquals("SPOTIFY", spotify.merchantNormalized());

        CsvParserService.ParsedTransaction salary = parsed.get(1);
        assertEquals(new BigDecimal("2500.00"), salary.amount());
    }

    @Test
    void parsesAsnExport() throws Exception {
        // Synthetic sample matching the headerless ASN Bank CSV export layout (fake data).
        // Columns: date, own IBAN, counterparty IBAN, counterparty name, 3x address,
        // currency, balance, currency, amount, journal date, value date, internal code,
        // global code, sequence, payment ref, description (single-quoted), statement no, tag.
        String csv = """
                02-04-2026,NL00ASNB0000000000,NL00INGB0000000001,NETFLIX,,,,EUR,1000.00,EUR,-9.99,02-04-2026,02-04-2026,9714,EIC,1810140,,'Europese incasso: Netflix abonnement',7,'Overig'
                11-05-2026,NL00ASNB0000000000,NL00INGB0000000002,Werkgever BV,,,,EUR,1150.00,EUR,2500.00,11-05-2026,11-05-2026,6853,BVZ,2508425,,'Salaris mei',10,'Overig'
                25-05-2026,NL00ASNB0000000000,,,,,,EUR,1145.00,EUR,-4.00,25-05-2026,25-05-2026,7241,AFB,759378,,'Kosten gebruik betaalrekening inclusief 1 betaalpas',11,'Bankkosten'
                """;

        List<CsvParserService.ParsedTransaction> parsed = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(3, parsed.size());

        CsvParserService.ParsedTransaction netflix = parsed.getFirst();
        assertEquals(LocalDate.of(2026, 4, 2), netflix.transactionDate());
        assertEquals(new BigDecimal("-9.99"), netflix.amount());
        assertEquals("Europese incasso: Netflix abonnement", netflix.description());
        assertEquals("NETFLIX", netflix.merchantNormalized());
        assertEquals(com.marnickseidel.moneyleaks.model.enums.PaymentMethod.DIRECT_DEBIT, netflix.paymentMethod());
        assertEquals("NL00INGB0000000001", netflix.counterpartyIban());

        CsvParserService.ParsedTransaction salary = parsed.get(1);
        assertEquals(LocalDate.of(2026, 5, 11), salary.transactionDate());
        assertEquals(new BigDecimal("2500.00"), salary.amount());
        assertEquals("Salaris mei", salary.description());

        CsvParserService.ParsedTransaction fee = parsed.get(2);
        assertEquals(new BigDecimal("-4.00"), fee.amount());
        assertEquals("Kosten gebruik betaalrekening inclusief 1 betaalpas", fee.description());
    }
}
