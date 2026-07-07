package com.marnickseidel.moneyleaks.banking.provider.enablebanking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the RS256 JSON Web Tokens that authorise every Enable Banking API call.
 *
 * <p>Enable Banking does not issue OAuth access tokens; instead each request carries a JWT
 * signed with the Application's RSA private key. The token is intentionally minimal:
 *
 * <pre>
 *   header  = { "typ": "JWT", "alg": "RS256", "kid": &lt;applicationId&gt; }
 *   payload = { "iss": "enablebanking.com", "aud": "api.enablebanking.com", "iat": now, "exp": now + ttl }
 *   token   = base64url(header) + "." + base64url(payload) + "." + base64url(RS256(signingInput))
 * </pre>
 *
 * <p>The signing logic is exposed as the pure static {@link #buildToken} so it can be unit
 * tested with an in-memory key pair, while instance methods add key loading and property wiring.
 *
 * @see <a href="https://enablebanking.com/docs/api/reference/#jwtAuthentication">JWT authentication</a>
 */
@Component
public class EnableBankingJwtService {

    public static final String ISSUER = "enablebanking.com";
    public static final String AUDIENCE = "api.enablebanking.com";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final EnableBankingProperties properties;
    private final ObjectMapper objectMapper;

    /** Lazily loaded and cached so we parse the PEM once, not on every request. */
    private volatile PrivateKey cachedPrivateKey;

    public EnableBankingJwtService(EnableBankingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a bearer token for the configured Application, valid for
     * {@link EnableBankingProperties#getJwtTtlSeconds()} seconds from now.
     *
     * @throws IllegalStateException if credentials are not configured or the key cannot be read
     */
    public String createToken() {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException(
                    "Enable Banking credentials are not configured (applicationId + private key required).");
        }
        return buildToken(
                properties.getApplicationId(),
                privateKey(),
                Instant.now(),
                properties.getJwtTtlSeconds(),
                objectMapper
        );
    }

    /** {@code "Bearer <jwt>"} value for the {@code Authorization} header. */
    public String bearerToken() {
        return "Bearer " + createToken();
    }

    /**
     * Pure token builder: given an application id, RSA private key, issue time and TTL, produce a
     * signed compact JWT. Kept static and side-effect free for straightforward unit testing.
     */
    public static String buildToken(
            String applicationId,
            PrivateKey privateKey,
            Instant issuedAt,
            long ttlSeconds,
            ObjectMapper objectMapper
    ) {
        long iat = issuedAt.getEpochSecond();
        long exp = iat + ttlSeconds;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "JWT");
        header.put("alg", "RS256");
        header.put("kid", applicationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("iat", iat);
        payload.put("exp", exp);

        String encodedHeader = base64Url(toJsonBytes(header, objectMapper));
        String encodedPayload = base64Url(toJsonBytes(payload, objectMapper));
        String signingInput = encodedHeader + "." + encodedPayload;

        byte[] signature = sign(signingInput.getBytes(StandardCharsets.US_ASCII), privateKey);
        return signingInput + "." + base64Url(signature);
    }

    private PrivateKey privateKey() {
        PrivateKey key = cachedPrivateKey;
        if (key == null) {
            synchronized (this) {
                key = cachedPrivateKey;
                if (key == null) {
                    key = loadPrivateKey();
                    cachedPrivateKey = key;
                }
            }
        }
        return key;
    }

    private PrivateKey loadPrivateKey() {
        String pem = readPem();
        return parsePkcs8PrivateKey(pem);
    }

    private String readPem() {
        String path = properties.getPrivateKeyPath();
        if (path != null && !path.isBlank()) {
            try {
                return Files.readString(Path.of(path), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to read Enable Banking private key at " + path, ex);
            }
        }
        String inline = properties.getPrivateKeyPem();
        if (inline != null && !inline.isBlank()) {
            return inline;
        }
        throw new IllegalStateException(
                "No Enable Banking private key configured (set enablebanking.private-key-path or private-key-pem).");
    }

    /**
     * Parse a PKCS#8 PEM ({@code -----BEGIN PRIVATE KEY-----}) into an RSA {@link PrivateKey}.
     * Keys in the legacy PKCS#1 form ({@code -----BEGIN RSA PRIVATE KEY-----}) are rejected with a
     * clear message; convert them with
     * {@code openssl pkcs8 -topk8 -nocrypt -in pkcs1.pem -out pkcs8.pem}.
     */
    static PrivateKey parsePkcs8PrivateKey(String pem) {
        String normalized = pem.trim();
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException(
                    "Enable Banking private key is in PKCS#1 format; convert it to PKCS#8 with "
                            + "'openssl pkcs8 -topk8 -nocrypt -in <key>.pem -out <key>-pkcs8.pem'.");
        }
        String base64 = normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid Enable Banking private key (expected PKCS#8 RSA PEM).", ex);
        }
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign Enable Banking JWT.", ex);
        }
    }

    private static byte[] toJsonBytes(Map<String, Object> value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise Enable Banking JWT segment.", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
