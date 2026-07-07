package com.marnickseidel.moneyleaks.banking.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a bank connection.
 *
 * @param institutionId provider's bank identifier (from the provider's institutions list;
 *                      for Enable Banking this is the ASPSP {@code name})
 * @param provider      optional provider key ({@code enablebanking} or {@code gocardless});
 *                      defaults to {@code enablebanking} when omitted
 */
public record StartBankConnectionRequest(
        @NotBlank String institutionId,
        String provider
) {
}
