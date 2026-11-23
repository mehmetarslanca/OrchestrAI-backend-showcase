package com.arslanca.orchestrai.core.security;

import com.arslanca.orchestrai.core.config.JwtProperties;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void shouldGenerateAndValidateToken() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(
                "01234567890123456789012345678901",
                60000,
                120000,
                "orchestrai-api"));
        provider.validateAndInitializeKey();

        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .githubId(42L)
                .username("memet")
                .role(UserRole.USER)
                .build();

        String token = provider.generateToken(user);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.extractUserId(token)).isEqualTo(userId);
        assertThat(provider.extractAllClaims(token).get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void shouldRejectTokenSignedWithDifferentIssuerContract() {
        JwtTokenProvider issuerA = new JwtTokenProvider(new JwtProperties(
                "01234567890123456789012345678901",
                60000,
                120000,
                "issuer-a"));
        issuerA.validateAndInitializeKey();

        JwtTokenProvider issuerB = new JwtTokenProvider(new JwtProperties(
                "01234567890123456789012345678901",
                60000,
                120000,
                "issuer-b"));
        issuerB.validateAndInitializeKey();

        String token = issuerA.generateToken(UserEntity.builder()
                .id(UUID.randomUUID())
                .githubId(42L)
                .username("memet")
                .role(UserRole.USER)
                .build());

        assertThat(issuerB.validateToken(token)).isFalse();
    }

    @Test
    void shouldFailFastWhenSecretIsTooShort() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(
                "short-secret",
                60000,
                120000,
                "orchestrai-api"));

        assertThatThrownBy(provider::validateAndInitializeKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
