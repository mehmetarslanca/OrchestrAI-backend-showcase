package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.config.GitHubAiOAuthProperties;
import com.arslanca.orchestrai.core.config.JwtProperties;
import com.arslanca.orchestrai.core.exception.type.SessionAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class GitHubAiOAuthStateService {

    private static final String PURPOSE = "github-ai-oauth-state";

    private final JwtProperties jwtProperties;
    private final GitHubAiOAuthProperties gitHubAiOAuthProperties;

    public GitHubAiOAuthStateService(
            final JwtProperties jwtProperties,
            final GitHubAiOAuthProperties gitHubAiOAuthProperties) {
        this.jwtProperties = jwtProperties;
        this.gitHubAiOAuthProperties = gitHubAiOAuthProperties;
    }

    public GitHubAiOAuthState issue(final UUID userId, final GitHubAiConnectionProduct product) {
        final OffsetDateTime issuedAt = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime expiresAt = issuedAt.plus(gitHubAiOAuthProperties.stateTtl());
        return new GitHubAiOAuthState(
                userId,
                product.connectionType(),
                product.surfaceKey(),
                UUID.randomUUID().toString(),
                issuedAt,
                expiresAt);
    }

    public String encode(final GitHubAiOAuthState state) {
        return Jwts.builder()
                .id(state.stateId())
                .subject(state.userId().toString())
                .claim("purpose", PURPOSE)
                .claim("connectionType", state.connectionType().name())
                .claim("surface", state.surface())
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(state.issuedAt().toInstant()))
                .expiration(Date.from(state.expiresAt().toInstant()))
                .signWith(signingKey())
                .compact();
    }

    public GitHubAiOAuthState decode(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("OAuth state bos olamaz.");
        }

        try {
            final Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PURPOSE.equals(claims.get("purpose", String.class))) {
                throw new SessionAuthenticationException("OAuth state purpose gecersiz.");
            }
            return new GitHubAiOAuthState(
                    UUID.fromString(claims.getSubject()),
                    com.arslanca.orchestrai.entity.enums.AiConnectionType.valueOf(claims.get("connectionType", String.class)),
                    claims.get("surface", String.class),
                    claims.getId(),
                    OffsetDateTime.ofInstant(claims.getIssuedAt().toInstant(), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
        } catch (final JwtException | IllegalArgumentException ex) {
            throw new SessionAuthenticationException("OAuth state gecersiz veya suresi dolmus.");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
