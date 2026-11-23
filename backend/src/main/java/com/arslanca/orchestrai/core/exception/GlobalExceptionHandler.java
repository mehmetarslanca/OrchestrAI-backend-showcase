package com.arslanca.orchestrai.core.exception;

import com.arslanca.orchestrai.core.exception.type.ApiErrorResponse;
import com.arslanca.orchestrai.core.exception.type.DuplicateRequestException;
import com.arslanca.orchestrai.core.exception.type.GitHubAuthenticationException;
import com.arslanca.orchestrai.core.exception.type.GoogleAuthenticationException;
import com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException;
import com.arslanca.orchestrai.core.exception.type.InsufficientCreditException;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.PromptRejectionException;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.core.exception.type.SecretEncryptionException;
import com.arslanca.orchestrai.core.exception.type.SessionAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PromptRejectionException.class)
    public ResponseEntity<ApiErrorResponse> handlePromptRejection(final PromptRejectionException ex) {
        log.warn("Prompt rejected by security filter: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                "PROMPT_REJECTED",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
    }

    @ExceptionHandler(LlmResponseParseException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmResponseParseError(
            final LlmResponseParseException ex,
            final WebRequest request) {
        if (shouldSuppressCommittedSseErrorResponse(request)) {
            log.warn("Response already committed for SSE stream. Suppressing LLM parse JSON error response.");
            return null;
        }

        log.error("LLM response parsing/validation failed: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "LLM_PARSE_ERROR",
                "AI model returned an unparseable response. Please try again.");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(GitHubAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubAuthenticationError(final GitHubAuthenticationException ex) {
        log.error("GitHub authentication error: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "GITHUB_API_ERROR",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(GoogleAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleGoogleAuthenticationError(final GoogleAuthenticationException ex) {
        log.error("Google authentication error: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                "GOOGLE_API_ERROR",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(SessionAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionAuthentication(final SessionAuthenticationException ex) {
        log.warn("Session authentication failed: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientCredit(final InsufficientCreditException ex) {
        log.warn("Insufficient credit: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_CREDIT",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateRequest(final DuplicateRequestException ex) {
        log.warn("Duplicate request rejected: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_REQUEST",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(final ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(PreparationStateException.class)
    public ResponseEntity<ApiErrorResponse> handlePreparationState(final PreparationStateException ex) {
        log.warn("Preparation state conflict: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "PREPARATION_STATE_CONFLICT",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(final IllegalStateException ex, final WebRequest request) {
        if (shouldSuppressSseIllegalStateResponse(request)) {
            log.warn("SSE stream cannot accept JSON error response. Suppressing IllegalState handler output.");
            return null;
        }

        log.warn("Illegal state encountered: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                "Sistemde geçici bir sorun oluştu.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(final IllegalArgumentException ex) {
        final var response = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HeavyModelResolutionException.class)
    public ResponseEntity<ApiErrorResponse> handleHeavyModelResolution(final HeavyModelResolutionException ex) {
        final var response = ApiErrorResponse.of(
                ex.status().value(),
                ex.error(),
                ex.getMessage());
        return ResponseEntity.status(ex.status()).body(response);
    }

    @ExceptionHandler(InfrastructureUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleInfrastructureUnavailable(
            final InfrastructureUnavailableException ex) {
        log.warn("Infrastructure unavailable: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(SecretEncryptionException.class)
    public ResponseEntity<ApiErrorResponse> handleSecretEncryption(final SecretEncryptionException ex) {
        log.error("Secret encryption failure: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SECRET_ENCRYPTION_ERROR",
                "Hassas veri islenirken beklenmeyen bir hata olustu.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validasyon hatası");

        final var response = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(final org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        final var response = ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(final Exception ex, WebRequest request) {
        if (shouldSuppressCommittedSseErrorResponse(request)) {
            log.warn("Response already committed for SSE stream. Suppressing generic JSON error response.");
            return null;
        }
        
        log.error("Unhandled Exception caught in GlobalExceptionHandler: ", ex);
        final var response = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "Beklenmeyen bir hata oluştu");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private boolean shouldSuppressCommittedSseErrorResponse(final WebRequest request) {
        if (!(request instanceof ServletWebRequest servletRequest)) {
            return false;
        }

        final HttpServletResponse response = servletRequest.getResponse();
        if (response == null || !response.isCommitted()) {
            return false;
        }

        final String accept = servletRequest.getHeader("Accept");
        final String contentType = response.getContentType();
        return (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private boolean shouldSuppressSseIllegalStateResponse(final WebRequest request) {
        if (!(request instanceof ServletWebRequest servletRequest)) {
            return false;
        }

        final HttpServletResponse response = servletRequest.getResponse();
        final String contentType = response == null ? null : response.getContentType();
        if (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }

        final String accept = servletRequest.getHeader("Accept");
        return response != null
                && response.isCommitted()
                && accept != null
                && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
