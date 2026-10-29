package com.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * Verifies PayPal webhook signatures offline, as documented by PayPal:
 * the transmission signature is SHA256withRSA over
 * "transmissionId|transmissionTime|webhookId|crc32(rawBody)", signed with the
 * certificate published at PAYPAL-CERT-URL (which must be served by paypal.com).
 */
@Slf4j
@Component
public class PayPalWebhookVerifier {

    @Value("${app.paypal.webhook-id:}")
    private String webhookId;

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    public boolean isConfigured() {
        return webhookId != null && !webhookId.isBlank();
    }

    public boolean verify(String rawBody, String transmissionId, String transmissionTime,
                          String certUrl, String authAlgo, String transmissionSig) {
        if (!isConfigured()) {
            log.error("PayPal webhook rejected — PAYPAL_WEBHOOK_ID is not configured");
            return false;
        }
        if (rawBody == null || transmissionId == null || transmissionTime == null
                || certUrl == null || transmissionSig == null) {
            log.warn("PayPal webhook rejected — missing signature headers");
            return false;
        }
        if (authAlgo != null && !"SHA256withRSA".equalsIgnoreCase(authAlgo)) {
            log.warn("PayPal webhook rejected — unsupported auth algorithm {}", authAlgo);
            return false;
        }
        if (!isPayPalCertUrl(certUrl)) {
            log.warn("PayPal webhook rejected — untrusted cert URL {}", certUrl);
            return false;
        }

        CRC32 crc = new CRC32();
        crc.update(rawBody.getBytes(StandardCharsets.UTF_8));
        String signedData = transmissionId + "|" + transmissionTime + "|" + webhookId + "|" + crc.getValue();

        try {
            PublicKey key = keyCache.computeIfAbsent(certUrl, this::loadPublicKey);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(signedData.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(transmissionSig));
        } catch (Exception e) {
            log.warn("PayPal webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    static boolean isPayPalCertUrl(String certUrl) {
        try {
            URI uri = URI.create(certUrl);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("paypal.com") || host.toLowerCase().endsWith(".paypal.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Downloads the signing certificate; overridden in tests. */
    protected PublicKey loadPublicKey(String certUrl) {
        try (InputStream in = URI.create(certUrl).toURL().openStream()) {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
            cert.checkValidity();
            return cert.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load PayPal certificate from " + certUrl, e);
        }
    }
}
