package com.arslanca.orchestrai.core.idempotency;

import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.exception.ApiErrorResponseWriter;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    private IdempotencyInterceptor interceptor;
    private ProjectGenerationFeatureProperties featureProperties;

    @BeforeEach
    void setUp() {
        featureProperties = new ProjectGenerationFeatureProperties();
        interceptor = new IdempotencyInterceptor(idempotencyStore, new ApiErrorResponseWriter(new ObjectMapper()), featureProperties);
    }

    @Test
    @DisplayName("GET requests bypass idempotency validation")
    void shouldSkipValidationForGetRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(idempotencyStore);
    }

    @Test
    @DisplayName("Missing idempotency key returns standardized 400 response")
    void shouldRejectWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MISSING_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("Too long idempotency key returns standardized 400 response")
    void shouldRejectWhenHeaderIsTooLong() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        request.addHeader("X-Idempotency-Key", "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("Duplicate idempotency key returns standardized 409 response")
    void shouldRejectDuplicateRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        request.addHeader("X-Idempotency-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(idempotencyStore.checkAndMark("valid-key")).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("DUPLICATE_REQUEST");
    }

    @Test
    @DisplayName("Redis outage returns standardized 503 response")
    void shouldRejectWhenIdempotencyStoreIsUnavailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        request.addHeader("X-Idempotency-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(idempotencyStore.checkAndMark("valid-key"))
                .thenThrow(new InfrastructureUnavailableException("Idempotency protection is temporarily unavailable."));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("Fresh idempotency key is accepted")
    void shouldAllowValidNewRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        request.addHeader("X-Idempotency-Key", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(idempotencyStore.checkAndMark("valid-key")).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Disabled legacy endpoint bypasses idempotency interceptor so controller can return 404")
    void shouldBypassDisabledLegacyEndpoint() throws Exception {
        featureProperties.setLegacyGenerateEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(idempotencyStore);
    }
}
