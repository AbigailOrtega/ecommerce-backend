package com.ecommerce.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "ZGVmYXVsdC1kZXYtc2VjcmV0LWtleS10aGF0LXNob3VsZC1iZS1jaGFuZ2VkLWluLXByb2R1Y3Rpb24=";
    private static final long ACCESS_EXPIRATION = 86400000L;
    private static final long REFRESH_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", REFRESH_EXPIRATION);
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("genera token a partir de Authentication")
        void fromAuthentication() {
            User principal = new User("user@test.com", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            String token = jwtTokenProvider.generateAccessToken(auth);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("genera token a partir de email")
        void fromEmail() {
            String token = jwtTokenProvider.generateAccessToken("user@test.com");

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("user@test.com");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("genera refresh token con el email correcto")
        void generatesRefreshToken() {
            String token = jwtTokenProvider.generateRefreshToken("admin@test.com");

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("admin@test.com");
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("retorna true para token válido")
        void validToken() {
            String token = jwtTokenProvider.generateAccessToken("user@test.com");
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("retorna false para token malformado")
        void malformedToken() {
            assertThat(jwtTokenProvider.validateToken("not.a.valid.token")).isFalse();
        }

        @Test
        @DisplayName("retorna false para string vacío")
        void emptyToken() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("retorna false para token con firma incorrecta")
        void wrongSignature() {
            String token = jwtTokenProvider.generateAccessToken("user@test.com");
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
            assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
        }
    }
}
