package com.arslanca.orchestrai.core.ai.provider;

import java.util.Locale;

public final class HeavyModelKeyNormalizer {

    private HeavyModelKeyNormalizer() {
    }

    public static String normalize(final String rawHeavyModelKey) {
        if (rawHeavyModelKey == null || rawHeavyModelKey.isBlank()) {
            return null;
        }

        final String normalized = rawHeavyModelKey.trim().toLowerCase(Locale.ROOT);
        final String[] parts = normalized.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("heavyModelKey formati provider:family seklinde olmalidir.");
        }
        return parts[0] + ":" + parts[1];
    }
}
