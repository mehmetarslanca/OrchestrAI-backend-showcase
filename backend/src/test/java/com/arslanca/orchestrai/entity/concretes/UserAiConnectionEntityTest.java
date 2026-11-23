package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAiConnectionEntityTest {

    @Test
    void shouldResetTransientArtifactsWhenMarkingConnectionPending() {
        UUID userId = UUID.randomUUID();
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(userId, AiConnectionType.OPENAI_BYOK, "openai", 1);
        ReflectionTestUtils.setField(entity, "encryptedAccessToken", "access-cipher");
        ReflectionTestUtils.setField(entity, "accessTokenKeyVersion", 2);
        ReflectionTestUtils.setField(entity, "encryptedRefreshToken", "refresh-cipher");
        ReflectionTestUtils.setField(entity, "refreshTokenKeyVersion", 3);
        ReflectionTestUtils.setField(entity, "expiresAt", OffsetDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(entity, "lastValidatedAt", OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(entity, "reauthRequiredAt", OffsetDateTime.now().plusHours(2));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerAccountLabel", "Acme Workspace");

        entity.markPending("new-cipher", "sk-o...5678", 7, metadata);
        metadata.put("mutatedAfterCall", true);

        assertThat(entity.getConnectionStatus()).isEqualTo(AiConnectionStatus.PENDING);
        assertThat(entity.getEncryptedApiKey()).isEqualTo("new-cipher");
        assertThat(entity.getApiKeyHint()).isEqualTo("sk-o...5678");
        assertThat(entity.getApiKeyKeyVersion()).isEqualTo(7);
        assertThat(entity.getEncryptedAccessToken()).isNull();
        assertThat(entity.getAccessTokenKeyVersion()).isNull();
        assertThat(entity.getEncryptedRefreshToken()).isNull();
        assertThat(entity.getRefreshTokenKeyVersion()).isNull();
        assertThat(entity.getExpiresAt()).isNull();
        assertThat(entity.getLastValidatedAt()).isNull();
        assertThat(entity.getReauthRequiredAt()).isNull();
        assertThat(entity.getMetadata())
                .containsEntry("providerAccountLabel", "Acme Workspace")
                .doesNotContainKey("mutatedAfterCall");
    }

    @Test
    void shouldMarkConnectionValidWithoutLosingProvidedMetadata() {
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(
                UUID.randomUUID(),
                AiConnectionType.OPENAI_BYOK,
                "openai",
                1);
        ReflectionTestUtils.setField(entity, "reauthRequiredAt", OffsetDateTime.now().plusHours(1));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("catalogRefreshedAt", "2026-06-09T12:00:00Z");

        entity.markValid(metadata);
        metadata.put("mutatedAfterCall", true);

        assertThat(entity.getConnectionStatus()).isEqualTo(AiConnectionStatus.VALID);
        assertThat(entity.getLastValidatedAt()).isNotNull();
        assertThat(entity.getReauthRequiredAt()).isNull();
        assertThat(entity.getMetadata())
                .containsEntry("catalogRefreshedAt", "2026-06-09T12:00:00Z")
                .doesNotContainKey("mutatedAfterCall");
    }

    @Test
    void shouldClearSecretsWhenConnectionIsRevoked() {
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(
                UUID.randomUUID(),
                AiConnectionType.OPENAI_BYOK,
                "openai",
                1);
        ReflectionTestUtils.setField(entity, "encryptedAccessToken", "access-cipher");
        ReflectionTestUtils.setField(entity, "accessTokenKeyVersion", 2);
        ReflectionTestUtils.setField(entity, "encryptedRefreshToken", "refresh-cipher");
        ReflectionTestUtils.setField(entity, "refreshTokenKeyVersion", 3);
        ReflectionTestUtils.setField(entity, "expiresAt", OffsetDateTime.now().plusDays(1));
        entity.markPending("api-cipher", "sk-o...5678", 4, Map.of("providerAccountLabel", "Acme Workspace"));

        entity.markRevoked(Map.of("revokedReason", "user-requested"));

        assertThat(entity.getConnectionStatus()).isEqualTo(AiConnectionStatus.REVOKED);
        assertThat(entity.getLastValidatedAt()).isNotNull();
        assertThat(entity.getEncryptedApiKey()).isNull();
        assertThat(entity.getApiKeyHint()).isNull();
        assertThat(entity.getApiKeyKeyVersion()).isNull();
        assertThat(entity.getEncryptedAccessToken()).isNull();
        assertThat(entity.getAccessTokenKeyVersion()).isNull();
        assertThat(entity.getEncryptedRefreshToken()).isNull();
        assertThat(entity.getRefreshTokenKeyVersion()).isNull();
        assertThat(entity.getExpiresAt()).isNull();
        assertThat(entity.getMetadata()).containsEntry("revokedReason", "user-requested");
    }

    @Test
    void shouldPersistOauthSecretsAndAccountMetadataWhenOauthConnectionCompletes() {
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(
                UUID.randomUUID(),
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "github");

        entity.markOauthConnected(
                "access-cipher",
                2,
                "refresh-cipher",
                3,
                OffsetDateTime.now().plusHours(1),
                "12345",
                "octocat",
                Map.of("runtimeTarget", "github-models"));

        assertThat(entity.getConnectionStatus()).isEqualTo(AiConnectionStatus.VALID);
        assertThat(entity.getEncryptedAccessToken()).isEqualTo("access-cipher");
        assertThat(entity.getEncryptedRefreshToken()).isEqualTo("refresh-cipher");
        assertThat(entity.getProviderAccountSubject()).isEqualTo("12345");
        assertThat(entity.getProviderAccountLabel()).isEqualTo("octocat");
        assertThat(entity.getMetadata()).containsEntry("runtimeTarget", "github-models");
    }

    @Test
    void shouldPreserveExistingOauthSecretsWhenAuthorizationRestarts() {
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingOauth(
                UUID.randomUUID(),
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "github");

        entity.markOauthConnected(
                "access-cipher",
                2,
                "refresh-cipher",
                3,
                OffsetDateTime.now().plusHours(1),
                "12345",
                "octocat",
                Map.of("runtimeTarget", "github-models"));

        entity.markOauthAuthorizationPending(Map.of("oauthState", Map.of("stateId", "reauth-1")));

        assertThat(entity.getConnectionStatus()).isEqualTo(AiConnectionStatus.VALID);
        assertThat(entity.getEncryptedAccessToken()).isEqualTo("access-cipher");
        assertThat(entity.getEncryptedRefreshToken()).isEqualTo("refresh-cipher");
        assertThat(entity.getProviderAccountSubject()).isEqualTo("12345");
        assertThat(entity.getProviderAccountLabel()).isEqualTo("octocat");
        assertThat(entity.getMetadata()).containsKey("oauthState");
    }
}
