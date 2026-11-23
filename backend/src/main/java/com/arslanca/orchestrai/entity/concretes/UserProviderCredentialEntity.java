package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
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

@Entity
@Table(name = "user_provider_credentials")
public class UserProviderCredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_key", nullable = false, length = 30)
    private String providerKey;

    @Column(name = "encrypted_secret", columnDefinition = "TEXT")
    private String encryptedSecret;

    @Column(name = "secret_hint", length = 64)
    private String secretHint;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProviderCredentialStatus status;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProviderCredentialEntity() {
    }

    public static UserProviderCredentialEntity createPending(
            final UUID userId,
            final String providerKey,
            final int keyVersion) {
        final UserProviderCredentialEntity entity = new UserProviderCredentialEntity();
        entity.userId = userId;
        entity.providerKey = providerKey;
        entity.status = ProviderCredentialStatus.PENDING;
        entity.keyVersion = keyVersion;
        entity.metadata = Map.of();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public ProviderCredentialStatus getStatus() {
        return status;
    }

    public OffsetDateTime getLastValidatedAt() {
        return lastValidatedAt;
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

    public void markPending(final String encryptedSecret, final String secretHint, final int keyVersion) {
        this.encryptedSecret = encryptedSecret;
        this.secretHint = secretHint;
        this.keyVersion = keyVersion;
        this.status = ProviderCredentialStatus.PENDING;
        this.lastValidatedAt = null;
        this.metadata = Map.of();
    }

    public void markValid(final Map<String, Object> metadata) {
        this.status = ProviderCredentialStatus.VALID;
        this.lastValidatedAt = OffsetDateTime.now();
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public void markInvalid(final Map<String, Object> metadata) {
        this.status = ProviderCredentialStatus.INVALID;
        this.lastValidatedAt = OffsetDateTime.now();
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public void markRevoked() {
        this.status = ProviderCredentialStatus.REVOKED;
        this.lastValidatedAt = OffsetDateTime.now();
        this.encryptedSecret = null;
        this.secretHint = null;
        this.metadata = Map.of();
    }
}
