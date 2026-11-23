package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.UserRole;

import java.util.UUID;

/**
 * SecurityContext'te saklanan immutable kullanıcı bilgisi.
 * Entity referansı tutmaz — sadece gerekli alanlar.
 */
public record AuthenticatedUser(
        UUID id,
        String username,
        UserRole role,
        UUID sessionId) {
    public static AuthenticatedUser from(final UserEntity entity) {
        return new AuthenticatedUser(
                entity.getId(),
                entity.getUsername(),
                entity.getRole(),
                null);
    }
}
