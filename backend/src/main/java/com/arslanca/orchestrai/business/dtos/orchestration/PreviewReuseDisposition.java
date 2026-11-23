package com.arslanca.orchestrai.business.dtos.orchestration;

public enum PreviewReuseDisposition {
    REUSE_PREPARATION_SELECTION,
    RESOLVE_ACCEPTED_STARTER;

    private static final String LEGACY_DOCS_ONLY_STARTER_KEY = "docs_only";

    public static PreviewReuseDisposition fromSelectedStarterKey(final String selectedStarterKey) {
        if (selectedStarterKey == null || selectedStarterKey.isBlank()) {
            return REUSE_PREPARATION_SELECTION;
        }
        if (LEGACY_DOCS_ONLY_STARTER_KEY.equalsIgnoreCase(selectedStarterKey.trim())) {
            return REUSE_PREPARATION_SELECTION;
        }
        return RESOLVE_ACCEPTED_STARTER;
    }

    public static String legacyDocsOnlyStarterKey() {
        return LEGACY_DOCS_ONLY_STARTER_KEY;
    }
}
