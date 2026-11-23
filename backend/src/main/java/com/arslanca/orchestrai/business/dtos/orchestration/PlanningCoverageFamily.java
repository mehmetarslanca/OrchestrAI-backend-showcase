package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanningCoverageFamily {
    FRONTEND_EXPERIENCE("frontend-experience", List.of("frontend", "web", "dashboard", "react", "screen", "user interface")),
    BACKEND_FOUNDATION("backend-foundation", List.of("backend", "api", "service", "auth", "rest", "spring", "controller")),
    ASYNC_PROCESSING("async-processing", List.of("worker", "async", "queue", "background", "job", "consumer", "event")),
    IDENTITY_AND_ACCESS("identity-and-access", List.of("identity", "access", "auth", "oauth", "sso", "rbac", "permission")),
    TENANCY_AND_ISOLATION("tenancy-and-isolation", List.of("tenant", "multi-tenant", "organization", "workspace", "isolation")),
    OPERATIONS_AND_DEPLOYMENT("operations-and-deployment", List.of("deploy", "ops", "platform", "kubernetes", "runbook", "observability", "infrastructure")),
    DATA_AND_STORAGE("data-and-storage", List.of("database", "postgres", "storage", "schema", "migration", "persistence", "data")),
    INTEGRATIONS("integrations", List.of("integration", "webhook", "stripe", "provider", "third-party", "external")),
    ANALYTICS_AND_QUERY("analytics-and-query", List.of("analytics", "reporting", "aggregation", "insights", "query", "metrics")),
    UNKNOWN("unknown", List.of());

    private final String value;
    private final List<String> tokens;

    PlanningCoverageFamily(final String value, final List<String> tokens) {
        this.value = value;
        this.tokens = List.copyOf(tokens);
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PlanningCoverageFamily fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final PlanningCoverageFamily family : values()) {
            if (family.value.equals(normalizedValue)) {
                return family;
            }
        }
        return UNKNOWN;
    }

    public boolean matches(final String corpus) {
        final String normalizedCorpus = corpus == null ? "" : corpus.toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(normalizedCorpus::contains);
    }

    public static List<PlanningCoverageFamily> matchedBy(final String corpus) {
        final EnumSet<PlanningCoverageFamily> matchedFamilies = EnumSet.noneOf(PlanningCoverageFamily.class);
        for (final PlanningCoverageFamily family : values()) {
            if (family == UNKNOWN) {
                continue;
            }
            if (family.matches(corpus)) {
                matchedFamilies.add(family);
            }
        }
        return List.copyOf(matchedFamilies);
    }
}
