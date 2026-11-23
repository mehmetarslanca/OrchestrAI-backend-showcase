package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.OAuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_identity_connections")
public class UserIdentityConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_login", length = 255)
    private String providerLogin;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserIdentityConnectionEntity() {
    }

    public static UserIdentityConnectionEntity create(
            final UUID userId,
            final OAuthProvider provider,
            final String providerUserId,
            final String providerLogin,
            final String email,
            final String avatarUrl) {
        final UserIdentityConnectionEntity entity = new UserIdentityConnectionEntity();
        entity.userId = userId;
        entity.provider = provider;
        entity.providerUserId = providerUserId;
        entity.providerLogin = providerLogin;
        entity.email = email;
        entity.avatarUrl = avatarUrl;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderLogin() {
        return providerLogin;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void updateProfile(final String providerLogin, final String email, final String avatarUrl) {
        this.providerLogin = providerLogin;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }
}
