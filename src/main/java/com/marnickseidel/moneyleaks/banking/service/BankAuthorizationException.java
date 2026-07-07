package com.marnickseidel.moneyleaks.banking.service;

/**
 * Thrown when a bank OAuth callback cannot be matched to a stored connection
 * (unknown state, wrong provider, or missing code).
 */
public class BankAuthorizationException extends RuntimeException {

    public BankAuthorizationException(String message) {
        super(message);
    }
}
