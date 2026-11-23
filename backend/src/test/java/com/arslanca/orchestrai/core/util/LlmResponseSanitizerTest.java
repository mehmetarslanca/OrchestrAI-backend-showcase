package com.arslanca.orchestrai.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmResponseSanitizerTest {

    @Test
    void shouldSkipBrokenBracePreambleAndExtractFirstBalancedJsonObject() {
        final String rawResponse = """
                Draft scratchpad: {not-json

                Final answer:
                {
                  "projectName": "Acme",
                  "executiveSummary": "summary"
                }
                """;

        final String extractedJson = LlmResponseSanitizer.extractJson(rawResponse);

        assertThat(extractedJson).contains("\"projectName\": \"Acme\"");
        assertThat(extractedJson).startsWith("{");
        assertThat(extractedJson).endsWith("}");
    }
}
