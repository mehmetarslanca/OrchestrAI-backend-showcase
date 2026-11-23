package com.arslanca.orchestrai.core.exception.type;

/**
 * Thrown when an LLM response cannot be parsed into the expected
 * structured format (JSON → DTO).
 *
 * <p>
 * This typically indicates the LLM produced malformed output
 * despite explicit formatting instructions. Handled by
 * {@code GlobalExceptionHandler} → 502 Bad Gateway.
 * </p>
 */
public class LlmResponseParseException extends RuntimeException {

    public LlmResponseParseException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public LlmResponseParseException(final String message) {
        super(message);
    }
}
