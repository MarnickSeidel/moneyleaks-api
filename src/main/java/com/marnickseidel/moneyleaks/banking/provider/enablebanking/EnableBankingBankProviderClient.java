package com.marnickseidel.moneyleaks.banking.provider.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marnickseidel.moneyleaks.banking.api.BankProviderClient;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionSession;
import com.marnickseidel.moneyleaks.banking.domain.BankConnectionStatus;
import com.marnickseidel.moneyleaks.banking.domain.ExternalAccount;
import com.marnickseidel.moneyleaks.banking.domain.ExternalTransaction;
import com.marnickseidel.moneyleaks.banking.domain.StartConnectionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enable Banking (<a href="https://enablebanking.com">enablebanking.com</a>) Open Banking adapter.
 *
 * <p>Enable Banking is the target real provider now that GoCardless does not onboard new
 * individuals. It differs from GoCardless in two important ways, both handled here:
 * <ol>
 *   <li><b>Auth:</b> every request carries a short-lived RS256 JWT signed with the Application's
 *       RSA private key (see {@link EnableBankingJwtService}) instead of a secret id/key pair.</li>
 *   <li><b>Flow:</b> authorisation ({@code POST /auth}) yields a consent URL and an
 *       {@code authorization_id}; after the user consents the returned {@code code} is exchanged
 *       for a session ({@code POST /sessions}) that lists account {@code uid}s, which then drive
 *       {@code GET /accounts/{uid}/details} and {@code GET /accounts/{uid}/transactions}.</li>
 * </ol>
 *
 * <p>Mapping onto {@link BankProviderClient}:
 * <ul>
 *   <li>{@link #startConnection} &rarr; {@code POST /auth}; the {@code authorization_id} becomes the
 *       external connection id and the {@code url} is the consent URL.</li>
 *   <li>{@link #authorizeSession(String)} &rarr; {@code POST /sessions} (invoked by the redirect
 *       callback once the bank returns a {@code code}); the resulting {@code session_id} replaces the
 *       stored external connection id and is what account/transaction reads use.</li>
 *   <li>{@link #getAccounts} &rarr; {@code GET /sessions/{session_id}} &rarr; {@code accounts[].uid}.</li>
 *   <li>{@link #getTransactions} &rarr; {@code GET /accounts/{uid}/transactions} (paged via
 *       {@code continuation_key}).</li>
 * </ul>
 *
 * <p>Like the GoCardless adapter, when no credentials are configured the client serves
 * deterministic sample data (guarded by {@link EnableBankingProperties#isSampleDataEnabled()}) so
 * the endpoints and the shared detection pipeline can be exercised locally; once credentials are
 * present it makes real HTTP calls and never falls back to samples.
 *
 * @see <a href="https://enablebanking.com/docs/api/reference/">Enable Banking API reference</a>
 */
@Component
public class EnableBankingBankProviderClient implements BankProviderClient {

    public static final String PROVIDER_KEY = "enablebanking";

    private static final Logger log = LoggerFactory.getLogger(EnableBankingBankProviderClient.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** Cap paging so a misbehaving ASPSP can never spin us forever. */
    private static final int MAX_TRANSACTION_PAGES = 50;

    private final EnableBankingProperties properties;
    private final EnableBankingJwtService jwtService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EnableBankingBankProviderClient(
            EnableBankingProperties properties,
            EnableBankingJwtService jwtService,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(properties.getApiUrl()).build();
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    /**
     * Start authorisation: {@code POST /auth} with the target ASPSP (our
     * {@link StartConnectionCommand#institutionId()} is the Enable Banking ASPSP {@code name}),
     * the configured country, a redirect URL and a random {@code state}. The response's
     * {@code url} is the consent link and {@code authorization_id} is our external connection id.
     */
    @Override
    public BankConnectionSession startConnection(StartConnectionCommand command) {
        Instant validUntil = Instant.now().plus(properties.getConsentValidDays(), ChronoUnit.DAYS);
        if (!liveCallsRequired()) {
            String authId = "sample-auth-" + UUID.randomUUID();
            String consentUrl = "https://auth.enablebanking.com/ais/start?sessionid=" + authId;
            log.info("[enablebanking:sample] startConnection aspsp={} -> {}", command.institutionId(), authId);
            return new BankConnectionSession(authId, consentUrl, BankConnectionStatus.CREATED, validUntil);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("access").put("valid_until", toIso8601(validUntil));
        ObjectNode aspsp = body.putObject("aspsp");
        aspsp.put("name", command.institutionId());
        aspsp.put("country", properties.getCountry());
        body.put("state", command.oauthState() != null ? command.oauthState() : UUID.randomUUID().toString());
        body.put("redirect_url", resolveRedirectUrl(command));
        body.put("psu_type", properties.getPsuType());

        JsonNode response = post("/auth", body);
        String authorizationId = text(response, "authorization_id");
        String consentUrl = text(response, "url");
        log.info("Started Enable Banking authorisation {} for aspsp {}",
                authorizationId, command.institutionId());
        return new BankConnectionSession(authorizationId, consentUrl, BankConnectionStatus.CREATED, validUntil);
    }

    /**
     * Exchange the authorisation {@code code} the bank returns to our redirect URL for an
     * account-data session: {@code POST /sessions}. Returns a session whose
     * {@code externalConnectionId} is the Enable Banking {@code session_id} to persist and use for
     * subsequent account/transaction reads. Not part of {@link BankProviderClient}; a redirect
     * callback endpoint drives this second leg of the flow.
     */
    public BankConnectionSession authorizeSession(String code) {
        if (!liveCallsRequired()) {
            String sessionId = "sample-session-" + UUID.randomUUID();
            return new BankConnectionSession(sessionId, null, BankConnectionStatus.ACTIVE,
                    Instant.now().plus(properties.getConsentValidDays(), ChronoUnit.DAYS));
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", code);
        JsonNode response = post("/sessions", body);
        String sessionId = text(response, "session_id");
        Instant validUntil = parseValidUntil(response.path("access").path("valid_until"));
        log.info("Authorised Enable Banking session {}", sessionId);
        return new BankConnectionSession(sessionId, null, BankConnectionStatus.ACTIVE, validUntil);
    }

    /**
     * Status of an account-data session: {@code GET /sessions/{session_id}}. A readable session is
     * {@code ACTIVE}; a 4xx (e.g. expired/invalid) maps to {@code EXPIRED}. Before the session
     * exchange the stored id is still an {@code authorization_id}, which is reported as
     * {@code CREATED}.
     */
    @Override
    public BankConnectionStatus getConnectionStatus(String externalConnectionId) {
        if (!liveCallsRequired()) {
            return BankConnectionStatus.ACTIVE;
        }
        try {
            JsonNode session = get("/sessions/" + externalConnectionId);
            String status = text(session, "status");
            if (status != null && status.equalsIgnoreCase("AUTHORIZED")) {
                return BankConnectionStatus.ACTIVE;
            }
            return session.hasNonNull("session_id") || session.has("accounts")
                    ? BankConnectionStatus.ACTIVE
                    : BankConnectionStatus.CREATED;
        } catch (RuntimeException ex) {
            log.warn("Enable Banking session {} not readable: {}", externalConnectionId, ex.getMessage());
            return BankConnectionStatus.EXPIRED;
        }
    }

    /**
     * Accounts granted for a session: {@code GET /sessions/{session_id}} exposes {@code accounts[]}
     * with each account's {@code uid} (used for later transaction reads), IBAN, name and currency.
     */
    @Override
    public List<ExternalAccount> getAccounts(String externalConnectionId) {
        if (!liveCallsRequired()) {
            return List.of(new ExternalAccount(
                    "sample-acc-" + externalConnectionId,
                    "NL00SAMP0123456789",
                    "Sample Current Account",
                    "EUR"
            ));
        }
        JsonNode session = get("/sessions/" + externalConnectionId);
        List<ExternalAccount> accounts = new ArrayList<>();
        for (JsonNode account : arrayOf(session, "accounts")) {
            accounts.add(mapAccount(account));
        }
        return accounts;
    }

    /**
     * Booked transactions for one account: {@code GET /accounts/{uid}/transactions?date_from&date_to},
     * following {@code continuation_key} pages. Enable Banking reports amounts as unsigned magnitudes
     * plus a {@code credit_debit_indicator}; {@link #mapTransaction} applies our sign convention
     * (debit = negative).
     */
    @Override
    public List<ExternalTransaction> getTransactions(String externalAccountId, LocalDate dateFrom, LocalDate dateTo) {
        if (!liveCallsRequired()) {
            return sampleTransactions(dateTo == null ? LocalDate.now() : dateTo);
        }
        List<ExternalTransaction> transactions = new ArrayList<>();
        String continuationKey = null;
        int pages = 0;
        do {
            StringBuilder uri = new StringBuilder("/accounts/").append(externalAccountId).append("/transactions");
            List<String> query = new ArrayList<>();
            if (dateFrom != null) {
                query.add("date_from=" + DATE.format(dateFrom));
            }
            if (dateTo != null) {
                query.add("date_to=" + DATE.format(dateTo));
            }
            if (continuationKey != null) {
                query.add("continuation_key=" + continuationKey);
            }
            if (!query.isEmpty()) {
                uri.append('?').append(String.join("&", query));
            }

            JsonNode page = get(uri.toString());
            for (JsonNode tx : arrayOf(page, "transactions")) {
                transactions.add(mapTransaction(tx));
            }
            continuationKey = text(page, "continuation_key");
        } while (continuationKey != null && !continuationKey.isBlank() && ++pages < MAX_TRANSACTION_PAGES);

        return transactions;
    }

    /**
     * A lapsed session cannot be renewed in place; start a fresh authorisation so the user
     * re-consents. Mirrors {@link #startConnection} but reuses the configured default ASPSP is not
     * possible without the institution, so callers should treat the returned consent URL as a new
     * authorisation for the same bank.
     */
    @Override
    public BankConnectionSession refreshConnection(String externalConnectionId) {
        if (!liveCallsRequired()) {
            return new BankConnectionSession(
                    externalConnectionId,
                    "https://auth.enablebanking.com/ais/start?sessionid=" + externalConnectionId + "&refresh=1",
                    BankConnectionStatus.CREATED,
                    Instant.now().plus(properties.getConsentValidDays(), ChronoUnit.DAYS)
            );
        }
        // Re-authorisation needs the ASPSP; the connection service supplies it via startConnection.
        throw new UnsupportedOperationException(
                "Enable Banking sessions cannot be refreshed in place; start a new authorisation for the bank.");
    }

    // --- Mapping (package-visible for unit testing) --------------------------------------------

    /**
     * Map one Enable Banking account object (from {@code /sessions} or {@code /accounts/{uid}/details})
     * to our provider-neutral {@link ExternalAccount}. The {@code uid} is the identifier used for
     * transaction reads.
     */
    static ExternalAccount mapAccount(JsonNode account) {
        String uid = optText(account, "uid");
        String iban = optText(account.path("account_id"), "iban");
        String name = optText(account, "name");
        String currency = optText(account, "currency");
        return new ExternalAccount(uid, iban, name, currency);
    }

    /**
     * Map one Enable Banking transaction to our provider-neutral {@link ExternalTransaction},
     * applying the app's sign convention: Enable Banking always reports a positive
     * {@code transaction_amount.amount}, and the direction comes from {@code credit_debit_indicator}
     * ({@code DBIT} = outgoing = negative, {@code CRDT} = incoming = positive). The counterparty is
     * taken from the creditor (for debits) or debtor (for credits), and the ISO-20022
     * {@code bank_transaction_code} is flattened into a hint string the shared
     * {@code TransactionMapper}/{@code PaymentMethodClassifier} already understands (e.g. a
     * direct-debit family code such as {@code DDBT}).
     */
    static ExternalTransaction mapTransaction(JsonNode tx) {
        String indicator = optText(tx, "credit_debit_indicator");
        boolean debit = "DBIT".equalsIgnoreCase(indicator);

        BigDecimal magnitude = new BigDecimal(requireText(tx.path("transaction_amount"), "amount"));
        BigDecimal amount = debit ? magnitude.negate() : magnitude;
        String currency = optText(tx.path("transaction_amount"), "currency");

        LocalDate bookingDate = firstDate(tx, "booking_date", "value_date", "transaction_date");
        String description = joinRemittance(tx.path("remittance_information"));

        JsonNode counterparty = debit ? tx.path("creditor") : tx.path("debtor");
        JsonNode counterpartyAccount = debit ? tx.path("creditor_account") : tx.path("debtor_account");
        String counterpartyName = optText(counterparty, "name");
        String counterpartyIban = optText(counterpartyAccount, "iban");

        if ((description == null || description.isBlank()) && counterpartyName != null) {
            description = counterpartyName;
        }

        String externalId = firstText(tx, "transaction_id", "entry_reference");
        String bankTransactionCode = flattenBankTransactionCode(tx.path("bank_transaction_code"));

        return new ExternalTransaction(
                externalId,
                bookingDate,
                amount,
                currency,
                description,
                counterpartyName,
                counterpartyIban,
                bankTransactionCode
        );
    }

    /**
     * Flatten the ISO-20022 {@code bank_transaction_code} (domain/family {@code code},
     * {@code sub_code} and human {@code description}) into a single hint string. Family codes such
     * as {@code DDBT}/{@code ESDD} (direct debit) or {@code CCRD}/{@code POSD} (card) are recognised
     * by the existing classifier, so no second classification engine is introduced.
     */
    private static String flattenBankTransactionCode(JsonNode code) {
        if (code == null || code.isMissingNode() || code.isNull()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, optText(code, "code"));
        appendIfPresent(sb, optText(code, "sub_code"));
        appendIfPresent(sb, optText(code, "description"));
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private List<ExternalTransaction> sampleTransactions(LocalDate anchor) {
        return List.of(
                sample("eb-tx-1", anchor.minusMonths(2).withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DDBT"),
                sample("eb-tx-2", anchor.minusMonths(1).withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DDBT"),
                sample("eb-tx-3", anchor.withDayOfMonth(3), "-12.99",
                        "NETFLIX.COM", "Netflix International B.V.", "DDBT"),
                sample("eb-tx-4", anchor.minusMonths(2).withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DDBT"),
                sample("eb-tx-5", anchor.minusMonths(1).withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DDBT"),
                sample("eb-tx-6", anchor.withDayOfMonth(18), "-40.00",
                        "Ziggo Services B.V.", "Ziggo Services B.V.", "DDBT"),
                sample("eb-tx-7", anchor.withDayOfMonth(25), "2500.00",
                        "Salary payment", "Employer B.V.", "RCDT"),
                sample("eb-tx-8", anchor.withDayOfMonth(12), "-8.40",
                        "Bakery downtown", "Bakery downtown", "POSD")
        );
    }

    private ExternalTransaction sample(String id, LocalDate date, String amount, String description,
                                       String counterpartyName, String code) {
        return new ExternalTransaction(
                id, date, new BigDecimal(amount), "EUR", description, counterpartyName, null, code);
    }

    // --- HTTP helpers --------------------------------------------------------------------------

    private JsonNode get(String uri) {
        return restClient.get()
                .uri(uri)
                .header("Authorization", jwtService.bearerToken())
                .header("Accept", "application/json")
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode post(String uri, JsonNode body) {
        return restClient.post()
                .uri(uri)
                .header("Authorization", jwtService.bearerToken())
                .header("Accept", "application/json")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private boolean liveCallsRequired() {
        return properties.hasCredentials() || !properties.isSampleDataEnabled();
    }

    private String resolveRedirectUrl(StartConnectionCommand command) {
        if (command.redirectUrl() != null && !command.redirectUrl().isBlank()) {
            return command.redirectUrl();
        }
        return properties.getRedirectUrl();
    }

    // --- JSON utilities ------------------------------------------------------------------------

    private static Iterable<JsonNode> arrayOf(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value instanceof ArrayNode array) {
            return array;
        }
        return List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String optText(JsonNode node, String field) {
        return text(node, field);
    }

    private static String requireText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalStateException("Missing required Enable Banking field: " + field);
        }
        return value;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static LocalDate firstDate(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return LocalDate.parse(value, DATE);
            }
        }
        return null;
    }

    private static String joinRemittance(JsonNode remittance) {
        if (!(remittance instanceof ArrayNode array) || array.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode line : array) {
            if (!line.isNull() && !line.asText().isBlank()) {
                parts.add(line.asText().trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value.trim());
        }
    }

    private static String toIso8601(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static Instant parseValidUntil(JsonNode validUntil) {
        if (validUntil == null || validUntil.isMissingNode() || validUntil.isNull()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(validUntil.asText()).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
