package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.auth.IssuedAuthSession;
import com.arslanca.orchestrai.core.config.JwtProperties;
import com.arslanca.orchestrai.core.exception.type.SessionAuthenticationException;
import com.arslanca.orchestrai.core.security.JwtTokenProvider;
import com.arslanca.orchestrai.dataaccess.abstracts.AuthSessionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.AuthSessionEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthSessionManager {

    private static final int REFRESH_TOKEN_BYTES = 48;

    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionManager(
            final AuthSessionRepository authSessionRepository,
            final UserRepository userRepository,
            final JwtTokenProvider jwtTokenProvider,
            final JwtProperties jwtProperties) {
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedAuthSession createSession(final UserEntity user) {
        final String refreshToken = generateRefreshToken();
        final OffsetDateTime refreshExpiresAt = OffsetDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshExpirationMs()));
        final AuthSessionEntity session = AuthSessionEntity.create(user.getId(), hashToken(refreshToken), refreshExpiresAt);
        final AuthSessionEntity savedSession = authSessionRepository.save(session);

        return new IssuedAuthSession(
                user,
                savedSession.getId(),
                jwtTokenProvider.generateToken(user, savedSession.getId()),
                refreshToken,
                jwtProperties.expirationMs(),
                jwtProperties.refreshExpirationMs());
    }

    @Transactional
    public IssuedAuthSession refreshSession(final String refreshToken) {
        final AuthSessionEntity session = authSessionRepository.findByTokenHashForUpdate(hashToken(refreshToken))
                .orElseThrow(() -> new SessionAuthenticationException("Refresh session not found or already rotated."));

        final OffsetDateTime now = OffsetDateTime.now();
        if (session.isRevoked() || session.isExpiredAt(now)) {
            throw new SessionAuthenticationException("Refresh session is no longer active.");
        }

        final UserEntity user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new SessionAuthenticationException("Session user could not be found."));

        final String nextRefreshToken = generateRefreshToken();
        session.rotate(hashToken(nextRefreshToken), now.plus(Duration.ofMillis(jwtProperties.refreshExpirationMs())));
        authSessionRepository.save(session);

        return new IssuedAuthSession(
                user,
                session.getId(),
                jwtTokenProvider.generateToken(user, session.getId()),
                nextRefreshToken,
                jwtProperties.expirationMs(),
                jwtProperties.refreshExpirationMs());
    }

    @Transactional
    public void revokeSession(final UUID sessionId) {
        if (sessionId == null) {
            return;
        }

        authSessionRepository.findById(sessionId).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.revoke();
                authSessionRepository.save(session);
            }
        });
    }

    private String generateRefreshToken() {
        final byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(final String token) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token.", ex);
        }
    }
}
