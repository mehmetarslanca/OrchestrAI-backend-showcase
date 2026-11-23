package com.arslanca.orchestrai.core.util;

import com.arslanca.orchestrai.core.exception.type.PromptRejectionException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Code-level pre-filter for user prompts — deterministic first line of defense.
 *
 * <p>
 * This validator runs <strong>before</strong> any LLM is called, providing
 * a cheap, non-LLM-dependent security layer. It catches:
 * <ul>
 * <li>Null, blank, or oversized prompts (resource exhaustion)</li>
 * <li>Common prompt injection / jailbreak patterns (deny-list)</li>
 * </ul>
 * </p>
 *
 * <p>
 * This does NOT replace LLM-level safety (Stage 1 still runs isSafe() checks),
 * but it prevents trivially detectable attacks from reaching the LLM at all.
 * </p>
 */
public final class PromptInputValidator {

    private PromptInputValidator() {
    }

    /** Maximum allowed prompt length in characters. */
    private static final int MAX_PROMPT_LENGTH = 10000;

    /**
     * Common prompt injection / jailbreak pattern fragments (case-insensitive).
     * This deny-list is intentionally conservative — it targets unambiguous
     * adversarial patterns, not legitimate technical language.
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all |your )?(previous |above |prior )?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (all |your )?(previous |above |prior )?instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget (everything|all|your instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now (a|an|the)\\s+\\w+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as (a|an|the)\\s+\\w+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("respond (only )?with[:\\s]+\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\{.*\"isMalicious\"\\s*:\\s*false.*\\})", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

    /**
     * Validates the raw user prompt before it reaches the LLM.
     *
     * @param userPrompt the user-submitted prompt
     * @return the sanitized and trimmed prompt
     * @throws IllegalArgumentException if the prompt is null, blank, or too long
     * @throws PromptRejectionException if a known injection pattern is detected
     */
    public static String validateAndSanitize(final String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        final String trimmed = userPrompt.trim();

        if (trimmed.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                    "Prompt exceeds maximum allowed length of " + MAX_PROMPT_LENGTH + " characters");
        }

        for (final Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                // Do NOT reveal which pattern matched — prevents attacker feedback loop
                throw new PromptRejectionException("Your request could not be processed.");
            }
        }
        
        return trimmed;
    }

    /**
     * Validates an aggregated chat context. This applies injection filtering
     * without enforcing the strict single-prompt length limits, allowing
     * legitimate large conversation histories to be processed.
     *
     * @param chatContext the aggregated conversation history
     * @return the sanitized and trimmed context
     * @throws IllegalArgumentException if the context is null or blank
     * @throws PromptRejectionException if a known injection pattern is detected
     */
    public static String validateAndSanitizeChatContext(final String chatContext) {
        if (chatContext == null || chatContext.isBlank()) {
            throw new IllegalArgumentException("Chat context cannot be blank");
        }

        final String trimmed = chatContext.trim();

        // Skip the MAX_PROMPT_LENGTH check for aggregated chat history.

        for (final Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                // Do NOT reveal which pattern matched
                throw new PromptRejectionException("Your request could not be processed.");
            }
        }

        return trimmed;
    }
}
