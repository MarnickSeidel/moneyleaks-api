package com.marnickseidel.moneyleaks.banking.provider.gocardless;

import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * GoCardless Bank Account Data (formerly Nordigen) adapter.
 *
 * <p><strong>Status: preparation layer.</strong> No live HTTP calls are made yet. Each
 * method documents the exact GoCardless interaction it will perform, and the code is
 * structured so the real implementation slots straight in. Until credentials are wired up,
 * the adapter serves deterministic sample data (when {@link GoCardlessProperties#isSampleDataEnabled()})
 * so the connection/sync endpoints and the shared detection pipeline can be exercised locally.
 *
 * <p>Real integration outline (GoCardless Bank Account Data API v2):
 * <ol>
 *   <li><b>Access token</b> - {@code POST /token/new/} with {@code secret_id}/{@code secret_key}
 *       to obtain a bearer {@code access} token (refresh with {@code /token/refresh/}).</li>
 *   <li><b>Institutions</b> - {@code GET /institutions/?country=XX} to list banks and their ids.</li>
 *   <li><b>End user agreement</b> (optional) - {@code POST /agreements/enduser/} to set scopes and
 *       the access window (max ~90 days of history / 90 day access).</li>
 *   <li><b>Requisition</b> - {@code POST /requisitions/} with the institution id, a redirect URL and
 *       (optionally) the agreement id. The response's {@code link} is the consent URL; its {@code id}
 *       is our external connection id.</li>
 *   <li><b>Consent</b> - redirect the user to {@code link}; they authenticate at their bank and are
 *       returned to our redirect URL.</li>
 *   <li><b>Accounts</b> - {@code GET /requisitions/{id}/} returns the granted {@code accounts} ids.</li>
 *   <li><b>Transactions</b> - {@code GET /accounts/{id}/transactions/?date_from=&date_to=} returns
 *       booked/pending transactions.</li>
 * </ol>
 *
 * @see <a href="https://developer.gocardless.com/bank-account-data/quick-start-guide">Quick start</a>
 */
@Component
public class GoCardlessBankProviderClient implements BankProviderClient {

    public static final String PROVIDER_KEY = "gocardless";

    private static final Logger log = LoggerFactory.getLogger(GoCardlessBankProviderClient.class);
    private static final int ACCESS_WINDOW_DAYS = 90;

    private final GoCardlessProperties properties;

    public GoCardlessBankProviderClient(GoCardlessProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    /**
     * Real flow: obtain an access token, then {@code POST /requisitions/} with the institution
     * id, redirect URL and optional agreement id. Returns the consent {@code link} and requisition
     * {@code id}.
     */
    @Override
    public BankConnectionSession startConnection(StartConnectionCommand command) {
        // TODO(gocardless): POST /token/new/ then POST /requisitions/ and map the response.
        if (liveCallsRequired()) {
            throw notImplemented("startConnection");
        }
        String externalConnectionId = "sample-req-" + UUID.randomUUID();
        String consentUrl = "https://ob.gocardless.com/psd2/start/" + externalConnectionId
                + "/" + safe(command.institutionId());
        log.info("[gocardless:sample] startConnection institution={} -> {}",
                command.institutionId(), externalConnectionId);
        return new BankConnectionSession(
                externalConnectionId,
                consentUrl,
                BankConnectionStatus.CREATED,
                Instant.now().plus(ACCESS_WINDOW_DAYS, ChronoUnit.DAYS)
        );
    }

    /**
     * Real flow: {@code GET /requisitions/{id}/} and map the requisition {@code status}
     * (e.g. {@code LN} linked -> ACTIVE, {@code EX} expired -> EXPIRED) to our status.
     */
    @Override
    public BankConnectionStatus getConnectionStatus(String externalConnectionId) {
        // TODO(gocardless): GET /requisitions/{id}/ and translate the status code.
        if (liveCallsRequired()) {
            throw notImplemented("getConnectionStatus");
        }
        // In sample mode we assume the user completed consent immediately.
        return BankConnectionStatus.ACTIVE;
    }

    /**
     * Real flow: {@code GET /requisitions/{id}/} to read the granted {@code accounts} ids, then
     * optionally {@code GET /accounts/{id}/details/} for IBAN/name/currency per account.
     */
    @Override
    public List<ExternalAccount> getAccounts(String externalConnectionId) {
        // TODO(gocardless): GET /requisitions/{id}/ -> accounts, then /accounts/{id}/details/.
        if (liveCallsRequired()) {
            throw notImplemented("getAccounts");
        }
        return List.of(new ExternalAccount(
                "sample-acc-" + externalConnectionId,
                "NL00SAMP0123456789",
                "Sample Current Account",
                "EUR"
        ));
    }

    /**
     * Real flow: {@code GET /accounts/{id}/transactions/?date_from=&date_to=} and map each
     * booked transaction (amount, currency, dates, remittance info, creditor/debtor) to
     * {@link ExternalTransaction}. Note GoCardless reports outgoing amounts as negative,
     * matching our convention.
     */
    @Override
    public List<ExternalTransaction> getTransactions(String externalAccountId, LocalDate dateFrom, LocalDate dateTo) {
        // TODO(gocardless): GET /accounts/{id}/transactions/ and map transactions.booked[].
        if (liveCallsRequired()) {
            throw notImplemented("getTransactions");
        }
        return sampleTransactions(dateTo == null ? LocalDate.now() : dateTo);
    }

    /**
     * Real flow: a lapsed requisition cannot be renewed in place - create a fresh requisition
     * ({@code POST /requisitions/}) so the user re-consents. Mirrors {@link #startConnection}.
     */
    @Override
    public BankConnectionSession refreshConnection(String externalConnectionId) {
        // TODO(gocardless): POST /requisitions/ for a new consent link.
        if (liveCallsRequired()) {
            throw notImplemented("refreshConnection");
        }
        return new BankConnectionSession(
                externalConnectionId,
                "https://ob.gocardless.com/psd2/start/" + externalConnectionId + "/refresh",
                BankConnectionStatus.CREATED,
                Instant.now().plus(ACCESS_WINDOW_DAYS, ChronoUnit.DAYS)
        );
    }

    private boolean liveCallsRequired() {
        // Once credentials exist we must not silently hand back sample data; and if sample
        // data is disabled without credentials, there is nothing to serve either.
        return properties.hasCredentials() || !properties.isSampleDataEnabled();
    }

    private UnsupportedOperationException notImplemented(String operation) {
        return new UnsupportedOperationException(
                "GoCardless live API call '" + operation + "' is not implemented yet. "
                        + "This is the preparation layer; enable gocardless.sample-data-enabled for local testing.");
    }

    /**
     * Deterministic set resembling a few months of a real account: a monthly streaming
     * subscription (detected), a monthly SEPA direct debit (detected), salary (ignored as
     * incoming) and a one-off card purchase (ignored). Lets the full pipeline be demonstrated.
     */
    private List<ExternalTransaction> sampleTransactions(LocalDate anchor) {
        return List.of(
                ext("gc-tx-1", anchor.minusMonths(2).withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DD"),
                ext("gc-tx-2", anchor.minusMonths(1).withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DD"),
                ext("gc-tx-3", anchor.withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DD"),
                ext("gc-tx-4", anchor.minusMonths(2).withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DD"),
                ext("gc-tx-5", anchor.minusMonths(1).withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DD"),
                ext("gc-tx-6", anchor.withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DD"),
                ext("gc-tx-7", anchor.withDayOfMonth(25), "2500.00",
                        "Salary payment", "Employer B.V.", "TRF"),
                ext("gc-tx-8", anchor.withDayOfMonth(12), "-8.40",
                        "Bakery downtown", "Bakery downtown", "PMNT")
        );
    }

    private ExternalTransaction ext(String id, LocalDate date, String amount, String description,
                                    String counterpartyName, String code) {
        return new ExternalTransaction(
                id,
                date,
                new BigDecimal(amount),
                "EUR",
                description,
                counterpartyName,
                null,
                code
        );
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}
