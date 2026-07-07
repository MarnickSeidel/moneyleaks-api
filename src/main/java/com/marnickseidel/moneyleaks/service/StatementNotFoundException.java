package com.marnickseidel.moneyleaks.service;

public class StatementNotFoundException extends RuntimeException {

    public StatementNotFoundException(Long id) {
        super("Statement not found: " + id);
    }
}
