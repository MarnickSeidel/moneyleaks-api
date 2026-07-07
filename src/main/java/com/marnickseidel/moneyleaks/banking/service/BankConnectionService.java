package com.marnickseidel.moneyleaks.banking.service;

import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import com.marnickseidel.moneyleaks.banking.domain.BankConnection;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;
import com.marnickseidel.moneyleaks.banking.provider.enablebanking.EnableBankingBankProviderClient;
import com.marnickseidel.moneyleaks.banking.provider.enablebanking.EnableBankingProperties;
import com.marnickseidel.moneyleaks.banking.provider.gocardless.GoCardlessProperties;
import com.marnickseidel.moneyleaks.banking.repository.BankConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Manages the {@link BankConnection} lifecycle: starting a consent session, reading status
 * (refreshed from the provider) and triggering a sync. Persists only opaque provider ids and
 * lifecycle state.
 */
@Service
public class BankConnectionService {

    /** No auth model yet - every connection is owned by this placeholder user. */
    public static final String PLACEHOLDER_USER_ID = "local-user";

    /**
     * Provider used when a start request omits an explicit provider. Enable Banking is the
     * default real provider now that GoCardless does not onboard new individuals; GoCardless
     * remains available by passing {@code provider=gocardless} explicitly.
     */
    public static final String DEFAULT_PROVIDER = EnableBankingBankProviderClient.PROVIDER_KEY;

    private static final Logger log = LoggerFactory.getLogger(BankConnectionService.class);

    private final BankConnectionRepository bankConnectionRepository;
    private final BankProviderRegistry providerRegistry;
    private final BankSyncService bankSyncService;
    private final GoCardlessProperties goCardlessProperties;
    private final EnableBankingProperties enableBankingProperties;

    public BankConnectionService(
            BankConnectionRepository bankConnectionRepository,
            BankProviderRegistry providerRegistry,
            BankSyncService bankSyncService,
            GoCardlessProperties goCardlessProperties,
            EnableBankingProperties enableBankingProperties
    ) {
        this.bankConnectionRepository = bankConnectionRepository;
        this.providerRegistry = providerRegistry;
        this.bankSyncService = bankSyncService;
        this.goCardlessProperties = goCardlessProperties;
        this.enableBankingProperties = enableBankingProperties;
    }

    /**
     * Start a connection with the given provider/institution and persist it in
     * {@link BankConnectionStatus#CREATED}. Returns both the stored connection and the
     * provider session (which carries the one-time consent URL the caller must redirect to).
     */
    @Transactional
    public StartResult start(String providerKey, String institutionId) {
        String resolvedProvider = (providerKey == null || providerKey.isBlank())
                ? DEFAULT_PROVIDER
                : providerKey;
        BankProviderClient provider = providerRegistry.get(resolvedProvider);

        BankConnectionSession session = provider.startConnection(new StartConnectionCommand(
                institutionId,
                redirectUrlFor(resolvedProvider),
                PLACEHOLDER_USER_ID
        ));

        Instant now = Instant.now();
        BankConnection connection = new BankConnection();
        connection.setUserId(PLACEHOLDER_USER_ID);
        connection.setProvider(resolvedProvider);
        connection.setInstitutionId(institutionId);
        connection.setExternalConnectionId(session.externalConnectionId());
        connection.setStatus(session.status() == null ? BankConnectionStatus.CREATED : session.status());
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        connection.setExpiresAt(session.expiresAt());

        BankConnection saved = bankConnectionRepository.save(connection);
        log.info("Started {} bank connection {} for institution {}",
                resolvedProvider, saved.getId(), institutionId);
        return new StartResult(saved, session.consentUrl());
    }

    /**
     * Load a connection and refresh its status from the provider (best-effort). If the
     * provider cannot currently report status (e.g. live API not yet implemented), the
     * stored status is returned unchanged.
     */
    @Transactional
    public BankConnection getRefreshed(Long id) {
        BankConnection connection = getRequired(id);
        try {
            BankProviderClient provider = providerRegistry.get(connection.getProvider());
            BankConnectionStatus status = provider.getConnectionStatus(connection.getExternalConnectionId());
            if (status != null && status != connection.getStatus()) {
                connection.setStatus(status);
                connection.setUpdatedAt(Instant.now());
                connection = bankConnectionRepository.save(connection);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not refresh status for connection {}: {}", id, ex.getMessage());
        }
        return connection;
    }

    @Transactional
    public BankSyncService.BankSyncResult sync(Long id) {
        BankConnection connection = getRequired(id);
        return bankSyncService.sync(connection);
    }

    @Transactional(readOnly = true)
    public BankConnection getRequired(Long id) {
        return bankConnectionRepository.findById(id)
                .orElseThrow(() -> new BankConnectionNotFoundException(id));
    }

    /** The redirect URL the resolved provider should return the user to after bank authentication. */
    private String redirectUrlFor(String providerKey) {
        return EnableBankingBankProviderClient.PROVIDER_KEY.equals(providerKey)
                ? enableBankingProperties.getRedirectUrl()
                : goCardlessProperties.getRedirectUrl();
    }

    /**
     * Result of starting a connection: the persisted entity plus the transient consent URL
     * (not stored, only relevant right after creation).
     */
    public record StartResult(BankConnection connection, String consentUrl) {
    }
}
