package com.marnickseidel.moneyleaks.model.enums;

/**
 * Where a {@link com.marnickseidel.moneyleaks.model.entity.BankTransaction} originated.
 * Detection treats every source identically; this only records provenance so CSV imports
 * and Open Banking syncs can be told apart and managed independently.
 */
public enum TransactionSource {
    CSV_UPLOAD,
    OPEN_BANKING
}
