package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_ai_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAiPreferenceEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "default_heavy_model_key", length = 120)
    private String defaultHeavyModelKey;

    @Column(name = "default_model_key", length = 120)
    private String defaultModelKey;

    @Column(name = "default_connection_id")
    private UUID defaultConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_auth_source", length = 20)
    private CredentialSource defaultAuthSource;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static UserAiPreferenceEntity create(final UUID userId) {
        final UserAiPreferenceEntity entity = new UserAiPreferenceEntity();
        entity.userId = userId;
        return entity;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDefaultHeavyModelKey() {
        return defaultHeavyModelKey;
    }

    public String getDefaultModelKey() {
        return defaultModelKey;
    }

    public UUID getDefaultConnectionId() {
        return defaultConnectionId;
    }

    public CredentialSource getDefaultAuthSource() {
        return defaultAuthSource;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateDefaultSelection(
            final String defaultModelKey,
            final UUID defaultConnectionId,
            final CredentialSource defaultAuthSource) {
        this.defaultHeavyModelKey = defaultModelKey;
        this.defaultModelKey = defaultModelKey;
        this.defaultConnectionId = defaultAuthSource == CredentialSource.PLATFORM ? null : defaultConnectionId;
        this.defaultAuthSource = defaultAuthSource;
    }

    public String resolveDefaultModelKey() {
        return defaultModelKey != null ? defaultModelKey : defaultHeavyModelKey;
    }
}
