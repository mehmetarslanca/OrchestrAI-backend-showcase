package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.auth.GoogleTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GoogleUserInfo;
import com.arslanca.orchestrai.business.dtos.auth.IdentityProfile;
import com.arslanca.orchestrai.business.dtos.auth.IssuedAuthSession;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.business.dtos.auth.AuthResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo;
import com.arslanca.orchestrai.business.abstracts.AuthService;
import com.arslanca.orchestrai.business.abstracts.UserManagementService;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Handles GitHub OAuth authentication flow.
 *
 * <p>
 * The method is split into three phases to avoid holding a database connection
 * during external HTTP calls to GitHub's API:
 * <ol>
 * <li><strong>Phase 1</strong> — External HTTP calls (no transaction)</li>
 * <li><strong>Phase 2</strong> — DB operations (short,
 * targeted @Transactional)</li>
 * <li><strong>Phase 3</strong> — JWT generation (no DB needed)</li>
 * </ol>
 * This prevents HikariCP connection pool starvation when GitHub's API is slow.
 * </p>
 */
@Service
@Slf4j
public class AuthManager implements AuthService {

    private final GitHubApiClient gitHubApiClient;
    private final GoogleApiClient googleApiClient;
    private final AuthSessionManager authSessionManager;
    private final UserManagementService userManagementService;
    private final EntityMapper entityMapper;

    public AuthManager(
            GitHubApiClient gitHubApiClient,
            GoogleApiClient googleApiClient,
            AuthSessionManager authSessionManager,
            UserManagementService userManagementService,
            EntityMapper entityMapper) {
        this.gitHubApiClient = gitHubApiClient;
        this.googleApiClient = googleApiClient;
        this.authSessionManager = authSessionManager;
        this.userManagementService = userManagementService;
        this.entityMapper = entityMapper;
    }


    @Override
    public AuthResponse authenticateWithGitHub(final String authorizationCode) {
        // Phase 1: External HTTP calls — NO transaction, no DB connection held.
        // If GitHub is slow (5-10s), this does NOT block a HikariCP connection.
        final GitHubTokenResponse tokenResponse = gitHubApiClient.exchangeCodeForToken(authorizationCode);
        log.info("GitHub token exchange completed successfully");

        final GitHubUserInfo userInfo = gitHubApiClient.fetchUserInfo(tokenResponse.accessToken());
        log.info("GitHub user info retrieved successfully");

        // Phase 2: DB operations — short, targeted transaction inside userManagementService.
        final UserEntity user = userManagementService.findOrCreateUser(new IdentityProfile(
                com.arslanca.orchestrai.entity.enums.OAuthProvider.GITHUB,
                String.valueOf(userInfo.id()),
                userInfo.login(),
                userInfo.email(),
                userInfo.avatarUrl()));

        final IssuedAuthSession issuedAuthSession = authSessionManager.createSession(user);
        log.info("JWT session generated for userId={} sessionId={}", user.getId(), issuedAuthSession.sessionId());

        return AuthResponse.withSession(
                issuedAuthSession.accessToken(),
                issuedAuthSession.accessExpiresInMs(),
                issuedAuthSession.refreshToken(),
                issuedAuthSession.refreshExpiresInMs(),
                entityMapper.toUserResponse(user));
    }

    @Override
    public AuthResponse authenticateWithGoogle(final String authorizationCode) {
        final GoogleTokenResponse tokenResponse = googleApiClient.exchangeCodeForToken(authorizationCode);
        log.info("Google token exchange completed successfully");

        final GoogleUserInfo userInfo = googleApiClient.fetchUserInfo(tokenResponse.accessToken());
        log.info("Google user info retrieved successfully");

        final UserEntity user = userManagementService.findOrCreateUser(new IdentityProfile(
                com.arslanca.orchestrai.entity.enums.OAuthProvider.GOOGLE,
                userInfo.sub(),
                userInfo.name(),
                userInfo.email(),
                userInfo.picture()));

        final IssuedAuthSession issuedAuthSession = authSessionManager.createSession(user);
        log.info("Google session generated for userId={} sessionId={}", user.getId(), issuedAuthSession.sessionId());

        return AuthResponse.withSession(
                issuedAuthSession.accessToken(),
                issuedAuthSession.accessExpiresInMs(),
                issuedAuthSession.refreshToken(),
                issuedAuthSession.refreshExpiresInMs(),
                entityMapper.toUserResponse(user));
    }

    @Override
    public AuthResponse refreshSession(final String refreshToken) {
        final IssuedAuthSession issuedAuthSession = authSessionManager.refreshSession(refreshToken);
        return AuthResponse.withSession(
                issuedAuthSession.accessToken(),
                issuedAuthSession.accessExpiresInMs(),
                issuedAuthSession.refreshToken(),
                issuedAuthSession.refreshExpiresInMs(),
                entityMapper.toUserResponse(issuedAuthSession.user()));
    }

    @Override
    public void logout(final UUID sessionId) {
        authSessionManager.revokeSession(sessionId);
    }
}
