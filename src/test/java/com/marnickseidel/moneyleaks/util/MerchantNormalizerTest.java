package com.marnickseidel.moneyleaks.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    void normalizesSpecialCharacters() {
        assertEquals("DISNEY PLUS", normalizer.normalize("Disney+ Subscription"));
        assertEquals("NETFLIX", normalizer.normalize("NETFLIX.COM Amsterdam"));
    }

    @Test
    void handlesBlankInput() {
        assertEquals("UNKNOWN", normalizer.normalize("   "));
    }

    @Test
    void groupsSepaHealthInsurerDespiteVaryingReferences() {
        String april = "Europese incasso: NL-kenmerk 6001704262919083 Rel.nr. 475036190 "
                + "Periode 01-05-2026/31-05-2026 CZZorgverzekering-Incassant ID: "
                + "NL18ZZZ410952220000-Kenmerk Machtiging: 475036190001001002-SDD0331055410";
        String may = "Europese incasso: NL-kenmerk 2001505266034063 Rel.nr. 475036190 "
                + "Periode 01-06-2026/30-06-2026 CZZorgverzekering-Incassant ID: "
                + "NL18ZZZ410952220000-Kenmerk Machtiging: 475036190001001002-SDD0334286071";

        assertEquals("CZ ZORGVERZEKERING", normalizer.normalize(april));
        assertEquals(normalizer.normalize(april), normalizer.normalize(may));
    }

    @Test
    void groupsSepaVodafoneDespiteVaryingInvoiceNumbers() {
        String april = "Europese incasso: NL-Klant Nr 510510112/402411481 April Factuur Nr "
                + "320176177115 zie vodafone.nl/my-Incassant ID: NL85ZZZ140522640000";
        String june = "Europese incasso: NL-Klant Nr 510510112/402411481 Juni Factuur Nr "
                + "320194517223 zie vodafone.nl/my-Incassant ID: NL85ZZZ140522640000";

        assertEquals("VODAFONE", normalizer.normalize(april));
        assertEquals(normalizer.normalize(april), normalizer.normalize(june));
    }

    @Test
    void groupsSepaReisverzekeringDespiteVaryingPeriod() {
        String april = "Europese incasso: NL-Reisverzekering 306884735 "
                + "Periode 02.04.2026-02.05.2026 FBTO";
        String may = "Europese incasso: NL-Reisverzekering 306884735 "
                + "Periode 02.05.2026-02.06.2026 FBTO";

        assertEquals(normalizer.normalize(april), normalizer.normalize(may));
    }

    @Test
    void keepsDistinctSepaCreditorsSeparate() {
        String insurer = "Europese incasso: NL-kenmerk 6001704262919083 Rel.nr. 475036190 "
                + "CZZorgverzekering-Incassant ID: NL18ZZZ410952220000";
        String telecom = "Europese incasso: NL-Klant Nr 510510112 zie vodafone.nl/my";

        org.junit.jupiter.api.Assertions.assertNotEquals(
                normalizer.normalize(insurer), normalizer.normalize(telecom));
    }
}
