package com.marnickseidel.moneyleaks.banking.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a bank connection.
 *
 * @param institutionId provider's bank identifier (from the provider's institutions list)
 * @param provider      optional provider key; defaults to {@code gocardless} when omitted
 */
public record StartBankConnectionRequest(
        @NotBlank String institutionId,
        String provider
) {
}
