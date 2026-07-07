package com.marnickseidel.moneyleaks.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
