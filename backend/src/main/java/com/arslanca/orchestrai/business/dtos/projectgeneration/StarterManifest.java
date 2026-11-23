package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record StarterManifest(
        String key,
        String title,
        String family,
        List<String> projectTypes,
        List<String> stackTags,
        List<String> supportsIdeTargets,
        StarterCatalogStatus status,
        StarterSourceKind sourceKind,
        String sourceRef,
        int priority,
        boolean docsOnlyFallbackAllowed,
        Map<String, Object> parameterSchema,
        List<String> constraints) {

    private static final Pattern SAFE_TOKEN_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    public StarterManifest {
        key = normalizeToken(key, "key");
        title = requireNonBlank(title, "title");
        family = normalizeToken(family, "family");
        projectTypes = normalizeTokenList(projectTypes, "projectTypes");
        stackTags = normalizeTokenList(stackTags, "stackTags");
        supportsIdeTargets = normalizeTokenList(supportsIdeTargets, "supportsIdeTargets");
        status = status == null ? StarterCatalogStatus.ACTIVE : status;
        sourceKind = Objects.requireNonNull(sourceKind, "sourceKind cannot be null");
        sourceRef = normalizeToken(sourceRef, "sourceRef");
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be zero or positive");
        }
        parameterSchema = parameterSchema == null ? Map.of() : Map.copyOf(parameterSchema);
        constraints = normalizeTextList(constraints);
    }

    public boolean supportsIdeTarget(final String ideTarget) {
        final String normalizedIdeTarget = normalizeToken(ideTarget, "ideTarget");
        return supportsIdeTargets.contains("generic") || supportsIdeTargets.contains(normalizedIdeTarget);
    }

    public boolean isSelectable() {
        return status.selectable();
    }

    public StarterCandidate toCandidate(final String reason) {
        return new StarterCandidate(
                key,
                title,
                family,
                sourceKind,
                sourceRef,
                stackTags,
                reason);
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeToken(final String value, final String fieldName) {
        final String normalizedValue = requireNonBlank(value, fieldName).toLowerCase();
        if (!SAFE_TOKEN_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters: " + value);
        }
        return normalizedValue;
    }

    private static List<String> normalizeTokenList(final List<String> values, final String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }

        final LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (final String value : values) {
            normalizedValues.add(normalizeToken(value, fieldName));
        }
        return List.copyOf(normalizedValues);
    }

    private static List<String> normalizeTextList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        final List<String> normalizedValues = new ArrayList<>();
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                normalizedValues.add(value.trim());
            }
        }
        return List.copyOf(normalizedValues);
    }
}
