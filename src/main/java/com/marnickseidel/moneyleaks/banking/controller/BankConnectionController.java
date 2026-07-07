package com.marnickseidel.moneyleaks.banking.controller;

import com.marnickseidel.moneyleaks.banking.api.dto.BankConnectionResponse;
import com.marnickseidel.moneyleaks.banking.api.dto.BankSyncResponse;
import com.marnickseidel.moneyleaks.banking.api.dto.StartBankConnectionRequest;
import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.service.BankConnectionService;
import com.marnickseidel.moneyleaks.banking.service.BankSyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Open Banking connection endpoints. Mirrors the style of the existing statement/subscription
 * controllers: thin controller delegating to services, records as payloads.
 */
@RestController
@RequestMapping("/api/bank-connections")
public class BankConnectionController {

    private final BankConnectionService bankConnectionService;

    public BankConnectionController(BankConnectionService bankConnectionService) {
        this.bankConnectionService = bankConnectionService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public BankConnectionResponse start(@Valid @RequestBody StartBankConnectionRequest request) {
        BankConnectionService.StartResult result =
                bankConnectionService.start(request.provider(), request.institutionId());
        return BankConnectionResponse.from(result.connection(), result.consentUrl());
    }

    /**
     * Enable Banking redirects the user's browser here after bank login with {@code code} and
     * {@code state} query parameters. Register this exact URL in the Enable Banking application.
     */
    @GetMapping("/callback")
    public BankConnectionResponse callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        BankConnection connection = bankConnectionService.completeAuthorization(code, state);
        return BankConnectionResponse.from(connection);
    }

    @GetMapping("/{id}")
    public BankConnectionResponse get(@PathVariable Long id) {
        BankConnection connection = bankConnectionService.getRefreshed(id);
        return BankConnectionResponse.from(connection);
    }

    @PostMapping("/{id}/sync")
    public BankSyncResponse sync(@PathVariable Long id) {
        BankSyncService.BankSyncResult result = bankConnectionService.sync(id);
        return new BankSyncResponse(
                id,
                result.accountsSynced(),
                result.transactionsImported(),
                result.subscriptionsDetected(),
                Instant.now()
        );
    }
}
