package com.marnickseidel.moneyleaks.banking.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction fetched from an Open Banking provider, normalised to the fields
 * MoneyLeaks needs. It is intentionally provider-neutral: a GoCardless, Plaid or Tink
 * adapter all map their raw payloads down to this shape before it reaches the core.
 *
 * <p>Sign convention matches the rest of the app: {@code amount} is negative for money
 * leaving the account (debits) and positive for incoming money (credits).
 *
 * @param externalId       provider's stable transaction id, used for de-duplication later
 * @param bookingDate      date the transaction was booked
 * @param amount           signed amount in {@code currency}
 * @param currency         ISO-4217 currency code
 * @param description      remittance information / counterparty text used for merchant matching
 * @param counterpartyName creditor or debtor name, when available
 * @param counterpartyIban creditor or debtor IBAN, when available
 * @param bankTransactionCode provider/bank transaction code (helps classify direct debits), when available
 */
public record ExternalTransaction(
        String externalId,
        LocalDate bookingDate,
        BigDecimal amount,
        String currency,
        String description,
        String counterpartyName,
        String counterpartyIban,
        String bankTransactionCode
) {
}
