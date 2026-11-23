package com.arslanca.orchestrai.core.idempotency;

import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.exception.ApiErrorResponseWriter;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    private final IdempotencyStore idempotencyStore;
    private final ApiErrorResponseWriter errorWriter;
    private final ProjectGenerationFeatureProperties featureProperties;

    public IdempotencyInterceptor(
            final IdempotencyStore idempotencyStore,
            final ApiErrorResponseWriter errorWriter,
            final ProjectGenerationFeatureProperties featureProperties) {
        this.idempotencyStore = idempotencyStore;
        this.errorWriter = errorWriter;
        this.featureProperties = featureProperties;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (isDisabledLegacyGenerateRequest(request)) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            errorWriter.write(response, HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "X-Idempotency-Key is required.");
            return false;
        }

        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            errorWriter.write(response, HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "X-Idempotency-Key is too long: max " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters.");
            return false;
        }

        final boolean isNew;
        try {
            isNew = idempotencyStore.checkAndMark(idempotencyKey);
        } catch (InfrastructureUnavailableException ex) {
            errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_UNAVAILABLE",
                    ex.getMessage());
            return false;
        }

        if (!isNew) {
            log.warn("Duplicate request rejected for idempotency key: {}", idempotencyKey);
            errorWriter.write(response, HttpStatus.CONFLICT, "DUPLICATE_REQUEST", "This request is currently being processed or has already been completed.");
            return false;
        }

        return true;
    }

    private boolean isDisabledLegacyGenerateRequest(final HttpServletRequest request) {
        if (featureProperties.isLegacyGenerateEnabled()) {
            return false;
        }

        final String path = request.getRequestURI();
        return "/api/v1/projects/generate".equals(path) || "/api/v1/projects/generate/".equals(path);
    }
}
