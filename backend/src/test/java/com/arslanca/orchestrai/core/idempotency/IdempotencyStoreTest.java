package com.arslanca.orchestrai.core.idempotency;

import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("İlk kez görülen key true döner")
    void shouldReturnTrueForNewKey() {
        when(valueOperations.setIfAbsent(eq("idempotency:new-key-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        final boolean result = idempotencyStore.checkAndMark("new-key-1");
        
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Aynı key ile ikinci çağrıda false döner (duplicate)")
    void shouldReturnFalseForDuplicateKey() {
        when(valueOperations.setIfAbsent(eq("idempotency:dup-key"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        final boolean result = idempotencyStore.checkAndMark("dup-key");
        
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis bağlantısı koptuğunda fail-closed exception fırlatır")
    void shouldThrowWhenRedisIsDown() {
        when(valueOperations.setIfAbsent(eq("idempotency:error-key"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> idempotencyStore.checkAndMark("error-key"))
                .isInstanceOf(InfrastructureUnavailableException.class)
                .hasMessageContaining("Idempotency protection is temporarily unavailable");
    }
    
    @Test
    @DisplayName("Bilinmeyen bir hata olduğunda fail-closed exception fırlatır")
    void shouldThrowOnUnexpectedException() {
        when(valueOperations.setIfAbsent(eq("idempotency:error-key"), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("Unknown Error"));

        assertThatThrownBy(() -> idempotencyStore.checkAndMark("error-key"))
                .isInstanceOf(InfrastructureUnavailableException.class)
                .hasMessageContaining("Idempotency protection is temporarily unavailable");
    }
}
