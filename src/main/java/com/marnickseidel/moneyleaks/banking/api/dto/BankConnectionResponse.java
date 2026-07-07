package com.marnickseidel.moneyleaks.banking.api.dto;

import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;

import java.time.Instant;

/**
 * API view of a {@link BankConnection}. {@code consentUrl} is only populated right after the
 * connection is started (the user must be redirected there); it is not persisted.
 */
public record BankConnectionResponse(
        Long id,
        String provider,
        String institutionId,
        BankConnectionStatus status,
        String consentUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {

    public static BankConnectionResponse from(BankConnection connection, String consentUrl) {
        return new BankConnectionResponse(
                connection.getId(),
                connection.getProvider(),
                connection.getInstitutionId(),
                connection.getStatus(),
                consentUrl,
                connection.getCreatedAt(),
                connection.getUpdatedAt(),
                connection.getExpiresAt()
        );
    }

    public static BankConnectionResponse from(BankConnection connection) {
        return from(connection, null);
    }
}
