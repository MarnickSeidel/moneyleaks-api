package com.marnickseidel.moneyleaks.model.enums;

/**
 * How the bank transaction was settled. Subscription detection should prefer
 * {@link #DIRECT_DEBIT}; card/POS and one-off online checkouts are usually not recurring bills.
 */
public enum PaymentMethod {
    DIRECT_DEBIT,
    CARD_POS,
    ONLINE_PAYMENT,
    TRANSFER,
    UNKNOWN
}
