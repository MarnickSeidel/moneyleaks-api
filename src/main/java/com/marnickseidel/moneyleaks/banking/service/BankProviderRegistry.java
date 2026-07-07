package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the right {@link BankProviderClient} for a connection by its provider key.
 * Adding a new provider is just registering another {@code BankProviderClient} bean - no
 * changes here or in the services that depend on this registry.
 */
@Component
public class BankProviderRegistry {

    private final Map<String, BankProviderClient> byKey;

    public BankProviderRegistry(List<BankProviderClient> providers) {
        this.byKey = providers.stream()
                .collect(Collectors.toMap(BankProviderClient::providerKey, Function.identity()));
    }

    public BankProviderClient get(String providerKey) {
        BankProviderClient client = byKey.get(providerKey);
        if (client == null) {
            throw new IllegalArgumentException("Unknown bank provider: " + providerKey);
        }
        return client;
    }
}
