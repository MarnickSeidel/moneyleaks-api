package com.marnickseidel.moneyleaks.banking.service;

public class BankConnectionNotFoundException extends RuntimeException {

    public BankConnectionNotFoundException(Long id) {
        super("Bank connection not found: " + id);
    }
}
