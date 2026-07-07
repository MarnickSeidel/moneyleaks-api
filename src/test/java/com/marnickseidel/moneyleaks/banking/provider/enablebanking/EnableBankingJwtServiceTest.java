package com.marnickseidel.moneyleaks.banking.provider.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnableBankingJwtServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsWellFormedRs256TokenWithExpectedHeaderAndClaims() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        String applicationId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        Instant issuedAt = Instant.ofEpochSecond(1_700_000_000L);
        long ttl = 3600;

        String token = EnableBankingJwtService.buildToken(
                applicationId, keyPair.getPrivate(), issuedAt, ttl, MAPPER);

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "compact JWT has header.payload.signature");

        JsonNode header = MAPPER.readTree(base64UrlDecode(parts[0]));
        assertEquals("JWT", header.get("typ").asText());
        assertEquals("RS256", header.get("alg").asText());
        assertEquals(applicationId, header.get("kid").asText());

        JsonNode payload = MAPPER.readTree(base64UrlDecode(parts[1]));
        assertEquals("enablebanking.com", payload.get("iss").asText());
        assertEquals("api.enablebanking.com", payload.get("aud").asText());
        assertEquals(1_700_000_000L, payload.get("iat").asLong());
        assertEquals(1_700_000_000L + ttl, payload.get("exp").asLong());
    }

    @Test
    void signatureVerifiesAgainstMatchingPublicKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        String token = EnableBankingJwtService.buildToken(
                "app-id", keyPair.getPrivate(), Instant.ofEpochSecond(1_700_000_000L), 3600, MAPPER);

        int lastDot = token.lastIndexOf('.');
        String signingInput = token.substring(0, lastDot);
        byte[] signature = Base64.getUrlDecoder().decode(token.substring(lastDot + 1));

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(signature), "RS256 signature must verify with the public key");
    }

    @Test
    void createTokenFailsWithoutCredentials() {
        EnableBankingProperties properties = new EnableBankingProperties();
        EnableBankingJwtService service = new EnableBankingJwtService(properties, MAPPER);
        assertThrows(IllegalStateException.class, service::createToken);
    }

    @Test
    void loadsPkcs8PemAndSignsWithConfiguredKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        EnableBankingProperties properties = new EnableBankingProperties();
        properties.setApplicationId("app-id");
        properties.setPrivateKeyPem(pem);
        EnableBankingJwtService service = new EnableBankingJwtService(properties, MAPPER);

        String token = service.createToken();
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void rejectsPkcs1KeyWithHelpfulMessage() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nMIIabc\n-----END RSA PRIVATE KEY-----";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EnableBankingJwtService.parsePkcs8PrivateKey(pkcs1));
        assertTrue(ex.getMessage().contains("PKCS#8"));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
