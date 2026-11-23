package com.arslanca.orchestrai.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.arslanca.orchestrai.core.idempotency.IdempotencyInterceptor;

class WebMvcConfigTest {

    @Test
    void shouldRegisterIdempotencyForLegacyGenerateAndCanonicalStartOnly() {
        final IdempotencyInterceptor idempotencyInterceptor = mock(IdempotencyInterceptor.class);
        final RateLimitInterceptor rateLimitInterceptor = mock(RateLimitInterceptor.class);
        final WebMvcConfig config = new WebMvcConfig(idempotencyInterceptor, rateLimitInterceptor);
        final InterceptorRegistry registry = new InterceptorRegistry();

        config.addInterceptors(registry);

        final List<?> registrations = (List<?>) ReflectionTestUtils.getField(registry, "registrations");
        assertThat(registrations).isNotNull();

        final Object idempotencyRegistration = registrations.stream()
                .filter(registration -> ReflectionTestUtils.getField(registration, "interceptor") == idempotencyInterceptor)
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        final List<String> includePatterns = (List<String>) ReflectionTestUtils.getField(idempotencyRegistration, "includePatterns");

        assertThat(includePatterns)
                .contains("/api/v*/projects/generate")
                .contains("/api/v*/project-generation/previews/*/accept")
                .contains("/api/v*/project-generation/start")
                .doesNotContain("/api/v*/project-generation/prepare");
    }
}
