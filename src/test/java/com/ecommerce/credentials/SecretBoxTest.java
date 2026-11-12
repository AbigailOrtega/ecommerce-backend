package com.ecommerce.credentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SecretBox")
class SecretBoxTest {

    private static String key(char c) {
        return Base64.getEncoder().encodeToString(String.valueOf(c).repeat(32).getBytes());
    }

    @Test
    @DisplayName("what it encrypts it reads back; the stored text reveals nothing and differs each time")
    void roundTrip() {
        SecretBox box = new SecretBox(key('a'));
        String a = box.encrypt("sk_live_secret");
        String b = box.encrypt("sk_live_secret");
        assertThat(a).startsWith("v1:").doesNotContain("secret").isNotEqualTo(b);
        assertThat(box.decrypt(a)).isEqualTo("sk_live_secret");
        assertThat(box.encrypt(null)).isNull();
        assertThat(box.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("another key cannot read it, nor can a changed ciphertext be read")
    void wrongKeyOrTampered() {
        String sealed = new SecretBox(key('a')).encrypt("sk_live_secret");
        assertThatThrownBy(() -> new SecretBox(key('b')).decrypt(sealed)).isInstanceOf(IllegalStateException.class);
        char[] chars = sealed.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> new SecretBox(key('a')).decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("without ENCRYPTION_KEY nothing can be saved; a short key is refused at startup")
    void noKey() {
        SecretBox box = new SecretBox("");
        assertThat(box.isConfigured()).isFalse();
        assertThatThrownBy(() -> box.encrypt("x")).hasMessageContaining("ENCRYPTION_KEY");
        assertThatThrownBy(() -> new SecretBox(Base64.getEncoder().encodeToString(new byte[16]))).hasMessageContaining("32 bytes");
    }
}
