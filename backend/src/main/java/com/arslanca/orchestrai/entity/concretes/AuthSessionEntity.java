package com.arslanca.orchestrai.entity.concretes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AuthSessionEntity() {
    }

    public static AuthSessionEntity create(final UUID userId, final String tokenHash, final OffsetDateTime expiresAt) {
        final AuthSessionEntity entity = new AuthSessionEntity();
        entity.userId = userId;
        entity.tokenHash = tokenHash;
        entity.expiresAt = expiresAt;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(final OffsetDateTime now) {
        return expiresAt.isBefore(now) || expiresAt.isEqual(now);
    }

    public void rotate(final String newTokenHash, final OffsetDateTime newExpiresAt) {
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = OffsetDateTime.now();
        this.revokedAt = null;
    }

    public void markUsed() {
        this.lastUsedAt = OffsetDateTime.now();
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }
}
