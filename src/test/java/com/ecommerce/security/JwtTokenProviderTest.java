package com.ecommerce.security;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtTokenProviderTest {

    static final String SECRET = "ZGVmYXVsdC1kZXYtc2VjcmV0LWtleS10aGF0LXNob3VsZC1iZS1jaGFuZ2VkLWluLXByb2R1Y3Rpb24=";
    private static final long ACCESS_EXPIRATION = 1800000L;
    private static final long REFRESH_EXPIRATION = 7776000000L;

    private JwtTokenProvider jwtTokenProvider;
    private User user;

    public static JwtTokenProvider newProvider() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", REFRESH_EXPIRATION);
        return provider;
    }

    /** A token as issued before access/refresh were separated: no "type" and no "pwd" claim. */
    public static String legacyToken(String email) {
        return Jwts.builder().subject(email).claim("role", "")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))).compact();
    }

    @BeforeEach
    void setUp() {
        jwtTokenProvider = newProvider();
        user = User.builder().email("user@test.com").password("$2a$10$hash-one").role(Role.CUSTOMER).build();
    }

    private Claims claims(String token) {
        return jwtTokenProvider.parseClaims(token).orElseThrow();
    }

    @Nested
    @DisplayName("token types")
    class Types {

        @Test
        @DisplayName("access token carries email, role and type=access")
        void accessToken() {
            Claims c = claims(jwtTokenProvider.generateAccessToken(user));

            assertThat(c.getSubject()).isEqualTo("user@test.com");
            assertThat(c.get("role", String.class)).isEqualTo("CUSTOMER");
            assertThat(jwtTokenProvider.isAccessToken(c)).isTrue();
            assertThat(jwtTokenProvider.isRefreshOrLegacyToken(c)).isFalse();
        }

        @Test
        @DisplayName("refresh token is not an access token")
        void refreshToken() {
            Claims c = claims(jwtTokenProvider.generateRefreshToken(user));

            assertThat(jwtTokenProvider.isAccessToken(c)).isFalse();
            assertThat(jwtTokenProvider.isRefreshOrLegacyToken(c)).isTrue();
        }

        @Test
        @DisplayName("access token expires after the configured 30 minutes; refresh after 90 days")
        void lifetimes() {
            Claims access = claims(jwtTokenProvider.generateAccessToken(user));
            Claims refresh = claims(jwtTokenProvider.generateRefreshToken(user));

            assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(ACCESS_EXPIRATION);
            assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime()).isEqualTo(REFRESH_EXPIRATION);
        }

        @Test
        @DisplayName("legacy tokens (no type) are only usable for refresh")
        void legacy() {
            Claims c = claims(legacyToken("user@test.com"));

            assertThat(jwtTokenProvider.isAccessToken(c)).isFalse();
            assertThat(jwtTokenProvider.isRefreshOrLegacyToken(c)).isTrue();
            assertThat(jwtTokenProvider.isCurrentFor(c, user)).isTrue();
        }
    }

    @Nested
    @DisplayName("password fingerprint")
    class Fingerprint {

        @Test
        @DisplayName("token is current while the password is unchanged")
        void samePassword() {
            Claims c = claims(jwtTokenProvider.generateAccessToken(user));
            assertThat(jwtTokenProvider.isCurrentFor(c, user)).isTrue();
        }

        @Test
        @DisplayName("tokens stop being current once the password changes")
        void passwordChanged() {
            Claims access = claims(jwtTokenProvider.generateAccessToken(user));
            Claims refresh = claims(jwtTokenProvider.generateRefreshToken(user));

            user.setPassword("$2a$10$hash-two");

            assertThat(jwtTokenProvider.isCurrentFor(access, user)).isFalse();
            assertThat(jwtTokenProvider.isCurrentFor(refresh, user)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseClaims")
    class Parse {

        @Test
        @DisplayName("empty for malformed, empty or tampered tokens")
        void invalid() {
            String token = jwtTokenProvider.generateAccessToken(user);
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

            assertThat(jwtTokenProvider.parseClaims("not.a.valid.token")).isEmpty();
            assertThat(jwtTokenProvider.parseClaims("")).isEmpty();
            assertThat(jwtTokenProvider.parseClaims(tampered)).isEmpty();
        }

        @Test
        @DisplayName("empty for expired tokens")
        void expired() {
            ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", -1000L);
            assertThat(jwtTokenProvider.parseClaims(jwtTokenProvider.generateAccessToken(user))).isEmpty();
        }
    }
}
