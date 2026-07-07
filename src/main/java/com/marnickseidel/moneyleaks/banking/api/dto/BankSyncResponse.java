package com.marnickseidel.moneyleaks.banking.api.dto;

import java.time.Instant;

public record BankSyncResponse(
        Long connectionId,
        int accountsSynced,
        int transactionsImported,
        int subscriptionsDetected,
        Instant syncedAt
) {
}
