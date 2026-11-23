package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

@Entity
@Table(name = "user_ai_connections")
public class UserAiConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 50)
    private AiConnectionType connectionType;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, length = 20)
    private AiConnectionAuthMethod authMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private AiConnectionBillingMode billingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 30)
    private AiConnectionStatus connectionStatus;

    @Column(name = "encrypted_access_token", columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "access_token_key_version")
    private Integer accessTokenKeyVersion;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "refresh_token_key_version")
    private Integer refreshTokenKeyVersion;

    @Column(name = "encrypted_api_key", columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "api_key_hint", length = 64)
    private String apiKeyHint;

    @Column(name = "api_key_key_version")
    private Integer apiKeyKeyVersion;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @Column(name = "reauth_required_at")
    private OffsetDateTime reauthRequiredAt;

    @Column(name = "provider_account_label", length = 255)
    private String providerAccountLabel;

    @Column(name = "provider_account_subject", length = 255)
    private String providerAccountSubject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserAiConnectionEntity() {
    }

    public static UserAiConnectionEntity createPendingByok(
            final UUID userId,
            final AiConnectionType connectionType,
            final String providerKey,
            final int apiKeyKeyVersion) {
        final UserAiConnectionEntity entity = new UserAiConnectionEntity();
        entity.userId = userId;
        entity.connectionType = connectionType;
        entity.providerKey = providerKey;
        entity.authMethod = AiConnectionAuthMethod.API_KEY;
        entity.billingMode = AiConnectionBillingMode.BYOK;
        entity.connectionStatus = AiConnectionStatus.PENDING;
        entity.apiKeyKeyVersion = apiKeyKeyVersion;
        entity.metadata = Map.of();
        return entity;
    }

    public static UserAiConnectionEntity createPendingOauth(
            final UUID userId,
            final AiConnectionType connectionType,
            final String providerKey) {
        final UserAiConnectionEntity entity = new UserAiConnectionEntity();
        entity.userId = userId;
        entity.connectionType = connectionType;
        entity.providerKey = providerKey;
        entity.authMethod = AiConnectionAuthMethod.OAUTH;
        entity.billingMode = AiConnectionBillingMode.USER_LINKED;
        entity.connectionStatus = AiConnectionStatus.PENDING;
        entity.metadata = Map.of();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AiConnectionType getConnectionType() {
        return connectionType;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public AiConnectionAuthMethod getAuthMethod() {
        return authMethod;
    }

    public AiConnectionBillingMode getBillingMode() {
        return billingMode;
    }

    public AiConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public Integer getAccessTokenKeyVersion() {
        return accessTokenKeyVersion;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public Integer getRefreshTokenKeyVersion() {
        return refreshTokenKeyVersion;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public String getApiKeyHint() {
        return apiKeyHint;
    }

    public Integer getApiKeyKeyVersion() {
        return apiKeyKeyVersion;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getLastValidatedAt() {
        return lastValidatedAt;
    }

    public OffsetDateTime getReauthRequiredAt() {
        return reauthRequiredAt;
    }

    public String getProviderAccountLabel() {
        return providerAccountLabel;
    }

    public String getProviderAccountSubject() {
        return providerAccountSubject;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markPending(
            final String encryptedApiKey,
            final String apiKeyHint,
            final int apiKeyKeyVersion,
            final Map<String, Object> metadata) {
        this.authMethod = AiConnectionAuthMethod.API_KEY;
        this.billingMode = AiConnectionBillingMode.BYOK;
        this.connectionStatus = AiConnectionStatus.PENDING;
        this.encryptedApiKey = encryptedApiKey;
        this.apiKeyHint = apiKeyHint;
        this.apiKeyKeyVersion = apiKeyKeyVersion;
        this.encryptedAccessToken = null;
        this.accessTokenKeyVersion = null;
        this.encryptedRefreshToken = null;
        this.refreshTokenKeyVersion = null;
        this.expiresAt = null;
        this.lastValidatedAt = null;
        this.reauthRequiredAt = null;
        this.metadata = copyMetadata(metadata);
    }

    public void markValid(final Map<String, Object> metadata) {
        this.connectionStatus = AiConnectionStatus.VALID;
        this.lastValidatedAt = OffsetDateTime.now();
        this.reauthRequiredAt = null;
        this.metadata = copyMetadata(metadata);
    }

    public void markInvalid(final Map<String, Object> metadata) {
        this.connectionStatus = AiConnectionStatus.INVALID;
        this.lastValidatedAt = OffsetDateTime.now();
        this.reauthRequiredAt = null;
        this.metadata = copyMetadata(metadata);
    }

    public void markRevoked(final Map<String, Object> metadata) {
        this.connectionStatus = AiConnectionStatus.REVOKED;
        this.lastValidatedAt = OffsetDateTime.now();
        this.reauthRequiredAt = null;
        this.encryptedApiKey = null;
        this.apiKeyHint = null;
        this.apiKeyKeyVersion = null;
        this.encryptedAccessToken = null;
        this.accessTokenKeyVersion = null;
        this.encryptedRefreshToken = null;
        this.refreshTokenKeyVersion = null;
        this.expiresAt = null;
        this.providerAccountLabel = null;
        this.providerAccountSubject = null;
        this.metadata = copyMetadata(metadata);
    }

    public void markOauthAuthorizationPending(final Map<String, Object> metadata) {
        this.authMethod = AiConnectionAuthMethod.OAUTH;
        this.billingMode = AiConnectionBillingMode.USER_LINKED;
        if (hasStoredOauthCredential()) {
            this.metadata = copyMetadata(metadata);
            return;
        }
        this.connectionStatus = AiConnectionStatus.PENDING;
        this.encryptedApiKey = null;
        this.apiKeyHint = null;
        this.apiKeyKeyVersion = null;
        this.encryptedAccessToken = null;
        this.accessTokenKeyVersion = null;
        this.encryptedRefreshToken = null;
        this.refreshTokenKeyVersion = null;
        this.expiresAt = null;
        this.lastValidatedAt = null;
        this.reauthRequiredAt = null;
        this.metadata = copyMetadata(metadata);
    }

    private boolean hasStoredOauthCredential() {
        return (this.encryptedAccessToken != null && !this.encryptedAccessToken.isBlank())
                || (this.encryptedRefreshToken != null && !this.encryptedRefreshToken.isBlank());
    }

    public void markOauthConnected(
            final String encryptedAccessToken,
            final int accessTokenKeyVersion,
            final String encryptedRefreshToken,
            final Integer refreshTokenKeyVersion,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> metadata) {
        applyOauthCredentialState(
                AiConnectionStatus.VALID,
                encryptedAccessToken,
                accessTokenKeyVersion,
                encryptedRefreshToken,
                refreshTokenKeyVersion,
                expiresAt,
                providerAccountSubject,
                providerAccountLabel,
                metadata);
    }

    public void markOauthExpired(
            final String encryptedAccessToken,
            final Integer accessTokenKeyVersion,
            final String encryptedRefreshToken,
            final Integer refreshTokenKeyVersion,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> metadata) {
        applyOauthCredentialState(
                AiConnectionStatus.EXPIRED,
                encryptedAccessToken,
                accessTokenKeyVersion,
                encryptedRefreshToken,
                refreshTokenKeyVersion,
                expiresAt,
                providerAccountSubject,
                providerAccountLabel,
                metadata);
    }

    public void markOauthEntitlementMissing(
            final String encryptedAccessToken,
            final Integer accessTokenKeyVersion,
            final String encryptedRefreshToken,
            final Integer refreshTokenKeyVersion,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> metadata) {
        applyOauthCredentialState(
                AiConnectionStatus.ENTITLEMENT_MISSING,
                encryptedAccessToken,
                accessTokenKeyVersion,
                encryptedRefreshToken,
                refreshTokenKeyVersion,
                expiresAt,
                providerAccountSubject,
                providerAccountLabel,
                metadata);
    }

    public void markOauthDegraded(
            final String encryptedAccessToken,
            final Integer accessTokenKeyVersion,
            final String encryptedRefreshToken,
            final Integer refreshTokenKeyVersion,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> metadata) {
        applyOauthCredentialState(
                AiConnectionStatus.DEGRADED,
                encryptedAccessToken,
                accessTokenKeyVersion,
                encryptedRefreshToken,
                refreshTokenKeyVersion,
                expiresAt,
                providerAccountSubject,
                providerAccountLabel,
                metadata);
    }

    private void applyOauthCredentialState(
            final AiConnectionStatus connectionStatus,
            final String encryptedAccessToken,
            final Integer accessTokenKeyVersion,
            final String encryptedRefreshToken,
            final Integer refreshTokenKeyVersion,
            final OffsetDateTime expiresAt,
            final String providerAccountSubject,
            final String providerAccountLabel,
            final Map<String, Object> metadata) {
        this.authMethod = AiConnectionAuthMethod.OAUTH;
        this.billingMode = AiConnectionBillingMode.USER_LINKED;
        this.connectionStatus = connectionStatus;
        this.encryptedApiKey = null;
        this.apiKeyHint = null;
        this.apiKeyKeyVersion = null;
        this.encryptedAccessToken = encryptedAccessToken;
        this.accessTokenKeyVersion = accessTokenKeyVersion;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.refreshTokenKeyVersion = refreshTokenKeyVersion;
        this.expiresAt = expiresAt;
        this.providerAccountSubject = providerAccountSubject;
        this.providerAccountLabel = providerAccountLabel;
        this.lastValidatedAt = OffsetDateTime.now();
        this.reauthRequiredAt = connectionStatus == AiConnectionStatus.REAUTH_REQUIRED
                || connectionStatus == AiConnectionStatus.EXPIRED
                ? OffsetDateTime.now()
                : null;
        this.metadata = copyMetadata(metadata);
    }

    public void markOauthReauthRequired(final Map<String, Object> metadata) {
        this.authMethod = AiConnectionAuthMethod.OAUTH;
        this.billingMode = AiConnectionBillingMode.USER_LINKED;
        this.connectionStatus = AiConnectionStatus.REAUTH_REQUIRED;
        this.lastValidatedAt = OffsetDateTime.now();
        this.reauthRequiredAt = OffsetDateTime.now();
        this.metadata = copyMetadata(metadata);
    }

    private Map<String, Object> copyMetadata(final Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
