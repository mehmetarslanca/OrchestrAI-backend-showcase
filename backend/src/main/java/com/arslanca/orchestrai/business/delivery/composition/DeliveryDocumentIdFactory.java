package com.arslanca.orchestrai.business.delivery.composition;

import java.util.Locale;

public final class DeliveryDocumentIdFactory {

    private DeliveryDocumentIdFactory() {
    }

    public static String globalRulesId() {
        return "rules";
    }

    public static String architectId() {
        return "architect";
    }

    public static String phaseId(final int order, final String title) {
        return "phase_" + order + "_" + slugify(title, "_");
    }

    public static String agentId(final String role) {
        return slugify(role, "-");
    }

    private static String slugify(final String value, final String separator) {
        if (value == null || value.isBlank()) {
            return "untitled";
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", separator)
                .replaceAll("^" + java.util.regex.Pattern.quote(separator) + "|"
                        + java.util.regex.Pattern.quote(separator) + "$", "");
        return normalized.isBlank() ? "untitled" : normalized;
    }
}
