package com.ecommerce.credentials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the stores' API secrets (Stripe, PayPal, Skydropx) for the database with AES-256-GCM.
 * Unlike a password (hashed, never read back) they must be decrypted to call the provider, so the
 * key (ENCRYPTION_KEY: 32 random bytes in base64, e.g. {@code openssl rand -base64 32}) lives only
 * in the server's environment: a copy of the database alone does not reveal them.
 */
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${app.encryption-key:}") String base64Key) {
        SecretKeySpec parsed = null;
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
            if (bytes.length != 32) throw new IllegalStateException("ENCRYPTION_KEY must be 32 bytes in base64 (openssl rand -base64 32)");
            parsed = new SecretKeySpec(bytes, "AES");
        }
        this.key = parsed;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        if (key == null) throw new IllegalStateException("Falta ENCRYPTION_KEY en el servidor: no se pueden guardar claves");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    /** The secret, or null when there is none; throws when it cannot be read (wrong or missing key). */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return null;
        if (key == null) throw new IllegalStateException("ENCRYPTION_KEY is not set: stored secrets cannot be read");
        if (!stored.startsWith(PREFIX)) throw new IllegalStateException("Unknown secret format");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt a stored secret (was ENCRYPTION_KEY changed?)", e);
        }
    }
}
