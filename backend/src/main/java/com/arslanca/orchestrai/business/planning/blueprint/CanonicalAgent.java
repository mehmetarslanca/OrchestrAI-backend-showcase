package com.arslanca.orchestrai.business.planning.blueprint;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalAgent(
        String role,
        String systemPrompt,
        List<String> responsibilities) {

    public CanonicalAgent {
        role = normalizeText(role);
        systemPrompt = normalizeText(systemPrompt);
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
    }

    private static String normalizeText(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
