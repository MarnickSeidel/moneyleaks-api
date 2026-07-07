package com.marnickseidel.moneyleaks.model.dto;

import com.marnickseidel.moneyleaks.model.enums.PaymentMethod;
import com.marnickseidel.moneyleaks.model.enums.SubscriptionInterval;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        String merchantNormalized,
        BigDecimal amount,
        String currency,
        SubscriptionInterval intervalType,
        int occurrenceCount,
        LocalDate firstSeen,
        LocalDate lastSeen,
        LocalDate nextExpectedCharge,
        BigDecimal confidence,
        String sampleDescription,
        String sourceIban,
        PaymentMethod paymentMethod
) {
}
