package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.core.config.JwtProperties;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final int MINIMUM_KEY_LENGTH_BYTES = 32;
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    private SecretKey cachedSecretKey;

    @PostConstruct
    void validateAndInitializeKey() {
        final byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "JWT secret key must be at least " + MINIMUM_KEY_LENGTH_BYTES
                            + " bytes (256-bit) for HS256. Current length: " + keyBytes.length + " bytes. "
                            + "Set JWT_SECRET in env.properties.");
        }
        this.cachedSecretKey = Keys.hmacShaKeyFor(keyBytes);
        LOGGER.info("JWT key validation passed and cached: {} bytes", keyBytes.length);
    }

    /**
     * Kullanıcı bilgilerinden JWT token üretir.
     * Claims: sub (userId), username, role, sid
     */
    public String generateToken(final UserEntity user) {
        return generateToken(user, null);
    }

    public String generateToken(final UserEntity user, final UUID sessionId) {
        final Date now = new Date();
        final Date expiration = new Date(now.getTime() + jwtProperties.expirationMs());

        final var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey());

        if (sessionId != null) {
            builder.claim("sid", sessionId.toString());
        }

        return builder.compact();
    }

    /**
     * Token'dan kullanıcı ID'sini çıkarır.
     */
    public UUID extractUserId(final String token) {
        final Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Token'dan tüm claims'leri çıkarır.
     */
    public Claims extractAllClaims(final String token) {
        return parseClaims(token);
    }

    /**
     * Token'ı doğrular. Geçersiz veya süresi dolmuş token'larda false döner.
     */
    public boolean validateToken(final String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return this.cachedSecretKey;
    }
}
