package com.arslanca.orchestrai.core.exception.type;

/**
 * Thrown when an LLM response is parseable JSON but violates the
 * semantic contract required by the current planning pass.
 */
public class LlmResponseValidationException extends LlmResponseParseException {

    public LlmResponseValidationException(final String message) {
        super(message);
    }
}
