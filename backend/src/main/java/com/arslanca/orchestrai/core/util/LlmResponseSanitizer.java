package com.arslanca.orchestrai.core.util;

import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;

/**
 * Utility for extracting clean JSON from raw LLM responses.
 *
 * <p>
 * LLMs often wrap JSON in markdown code fences or prepend/append prose
 * despite being instructed not to. This utility uses a balanced-bracket
 * scan to robustly extract the first complete JSON object ({...}),
 * regardless of surrounding text.
 * </p>
 */
public final class LlmResponseSanitizer {

    private static final int ASCII_CONTROL_MAX = 0x20;

    private LlmResponseSanitizer() {
    }

    /**
     * Extracts the first complete, balanced JSON object from the raw LLM response.
     *
     * <p>
     * Uses a bracket-counting scanner that properly handles:
     * <ul>
     * <li>Nested JSON objects and arrays</li>
     * <li>String literals (including escaped quotes)</li>
     * <li>Markdown code fences ({@code ```json ... ```})</li>
     * <li>Leading/trailing prose text</li>
     * <li>Prose containing {@code '}'} characters after the JSON object</li>
     * </ul>
     * </p>
     *
     * @param rawResponse the raw string returned by the LLM
     * @return the extracted JSON string, ready for parsing
     * @throws LlmResponseParseException if no valid JSON object is found
     */
    public static String extractJson(final String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new LlmResponseParseException("LLM returned an empty response");
        }

        final int start = rawResponse.indexOf('{');
        if (start == -1) {
            throw new LlmResponseParseException(
                    "No JSON object found in LLM response. Response length: " + rawResponse.length());
        }

        for (int candidateStart = start; candidateStart != -1; candidateStart = rawResponse.indexOf('{', candidateStart + 1)) {
            if (!looksLikeJsonObjectStart(rawResponse, candidateStart)) {
                continue;
            }

            final int end = findBalancedJsonObjectEnd(rawResponse, candidateStart);
            if (end != -1) {
                return escapeControlCharactersInsideJsonStrings(rawResponse.substring(candidateStart, end + 1));
            }
        }

        throw new LlmResponseParseException(
                "Unbalanced JSON object in LLM response. Response length: " + rawResponse.length());
    }

    private static boolean looksLikeJsonObjectStart(final String rawResponse, final int candidateStart) {
        for (int i = candidateStart + 1; i < rawResponse.length(); i++) {
            final char value = rawResponse.charAt(i);
            if (Character.isWhitespace(value)) {
                continue;
            }
            return value == '"' || value == '}';
        }

        return false;
    }

    private static int findBalancedJsonObjectEnd(final String rawResponse, final int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < rawResponse.length(); i++) {
            final char c = rawResponse.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String escapeControlCharactersInsideJsonStrings(final String json) {
        final StringBuilder sanitized = new StringBuilder(json.length() + 32);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escaped) {
                sanitized.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sanitized.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                sanitized.append(c);
                inString = !inString;
                continue;
            }

            if (inString && c < ASCII_CONTROL_MAX) {
                sanitized.append(escapeControlCharacter(c));
                continue;
            }

            sanitized.append(c);
        }

        return sanitized.toString();
    }

    private static String escapeControlCharacter(final char value) {
        return switch (value) {
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> String.format("\\u%04x", (int) value);
        };
    }
}
