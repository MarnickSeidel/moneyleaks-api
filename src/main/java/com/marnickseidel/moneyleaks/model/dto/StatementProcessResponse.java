package com.marnickseidel.moneyleaks.model.dto;

import com.marnickseidel.moneyleaks.model.enums.StatementStatus;

import java.time.Instant;

public record StatementProcessResponse(
        Long statementId,
        StatementStatus status,
        int transactionsImported,
        int subscriptionsDetected,
        Instant processedAt
) {
}
