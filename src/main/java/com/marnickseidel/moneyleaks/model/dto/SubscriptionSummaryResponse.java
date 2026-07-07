package com.marnickseidel.moneyleaks.model.dto;

import java.math.BigDecimal;

public record SubscriptionSummaryResponse(
        int activeSubscriptions,
        BigDecimal estimatedMonthlyTotal,
        String currency
) {
}
