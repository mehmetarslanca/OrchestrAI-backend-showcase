package com.arslanca.orchestrai.core.exception.type;

/**
 * Thrown when the Router/Analyzer (Stage 1) detects a malicious, harmful,
 * or prompt-injection attempt in the user's input.
 *
 * <p>
 * Handled by {@code GlobalExceptionHandler} → 422 Unprocessable Entity.
 * </p>
 */
public class PromptRejectionException extends RuntimeException {

    public PromptRejectionException(final String message) {
        super(message);
    }
}
