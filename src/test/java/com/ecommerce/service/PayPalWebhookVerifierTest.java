package com.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class PayPalWebhookVerifierTest {

    private static final String CERT_URL = "https://api.paypal.com/v1/notifications/certs/CERT-123";
    private static final String BODY = "{\"event_type\":\"PAYMENT.CAPTURE.REFUNDED\",\"resource\":{\"id\":\"CAP-1\"}}";
    private static final String TX_ID = "tx-001";
    private static final String TX_TIME = "2026-10-29T12:00:00Z";
    private static final String WEBHOOK_ID = "WH-123";

    private KeyPair keyPair;
    private PayPalWebhookVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        verifier = new PayPalWebhookVerifier() {
            @Override
            protected PublicKey loadPublicKey(String certUrl) {
                return keyPair.getPublic();
            }
        };
        ReflectionTestUtils.setField(verifier, "webhookId", WEBHOOK_ID);
    }

    private String sign(String body, String webhookId) throws Exception {
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update((TX_ID + "|" + TX_TIME + "|" + webhookId + "|" + crc.getValue()).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    @Test
    @DisplayName("accepts a correctly signed webhook")
    void validSignature() throws Exception {
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", sign(BODY, WEBHOOK_ID))).isTrue();
    }

    @Test
    @DisplayName("rejects a body that was modified after signing")
    void tamperedBody() throws Exception {
        String sig = sign(BODY, WEBHOOK_ID);
        assertThat(verifier.verify(BODY.replace("CAP-1", "CAP-2"), TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", sig)).isFalse();
    }

    @Test
    @DisplayName("rejects a signature made for another webhook id")
    void otherWebhookId() throws Exception {
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", sign(BODY, "WH-OTHER"))).isFalse();
    }

    @Test
    @DisplayName("rejects certificates not served over https from paypal.com")
    void untrustedCertUrl() throws Exception {
        String sig = sign(BODY, WEBHOOK_ID);
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, "https://evil.example.com/cert", "SHA256withRSA", sig)).isFalse();
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, "https://paypal.com.evil.io/cert", "SHA256withRSA", sig)).isFalse();
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, "http://api.paypal.com/cert", "SHA256withRSA", sig)).isFalse();
    }

    @Test
    @DisplayName("rejects requests with missing signature headers or garbage signature")
    void missingOrGarbage() {
        assertThat(verifier.verify(BODY, null, TX_TIME, CERT_URL, "SHA256withRSA", "x")).isFalse();
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", null)).isFalse();
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", "not-base64!!")).isFalse();
    }

    @Test
    @DisplayName("rejects everything when PAYPAL_WEBHOOK_ID is not configured")
    void notConfigured() throws Exception {
        String sig = sign(BODY, "");
        ReflectionTestUtils.setField(verifier, "webhookId", "");
        assertThat(verifier.isConfigured()).isFalse();
        assertThat(verifier.verify(BODY, TX_ID, TX_TIME, CERT_URL, "SHA256withRSA", sig)).isFalse();
    }
}
