package com.marnickseidel.moneyleaks.model.dto;

import com.marnickseidel.moneyleaks.model.enums.StatementStatus;

import java.time.Instant;

public record StatementUploadResponse(
        Long id,
        String filename,
        String contentHash,
        StatementStatus status,
        Instant uploadedAt,
        boolean duplicate
) {
}
