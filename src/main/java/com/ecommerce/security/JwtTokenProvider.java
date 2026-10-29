package com.ecommerce.security;

import com.ecommerce.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Issues two kinds of JWT, told apart by the "type" claim:
 * short-lived access tokens (sent on every request) and long-lived refresh tokens
 * (only accepted by /api/auth/refresh). Both carry a fingerprint of the user's password
 * hash ("pwd"), so changing the password invalidates every token issued before.
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TYPE_ACCESS, accessTokenExpiration);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TYPE_REFRESH, refreshTokenExpiration);
    }

    private String generateToken(User user, String type, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole() != null ? user.getRole().name() : "")
                .claim("type", type)
                .claim("pwd", passwordFingerprint(user))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** Claims of a correctly signed, unexpired token; empty otherwise. */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException | java.util.NoSuchElementException e) {
            return Optional.empty();
        }
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    /**
     * Tokens issued before access/refresh were separated have no "type" claim. They are only
     * accepted by /refresh, so sessions opened before the change renew transparently.
     */
    public boolean isRefreshOrLegacyToken(Claims claims) {
        String type = claims.get("type", String.class);
        return type == null || TYPE_REFRESH.equals(type);
    }

    /** False once the user's password changed after the token was issued (legacy tokens have no fingerprint). */
    public boolean isCurrentFor(Claims claims, User user) {
        String pwd = claims.get("pwd", String.class);
        if (pwd == null) return claims.get("type", String.class) == null;
        return MessageDigest.isEqual(
                pwd.getBytes(StandardCharsets.UTF_8),
                passwordFingerprint(user).getBytes(StandardCharsets.UTF_8));
    }

    private static String passwordFingerprint(User user) {
        String hash = user.getPassword() != null ? user.getPassword() : "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
