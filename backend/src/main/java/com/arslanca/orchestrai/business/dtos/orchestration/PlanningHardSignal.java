package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanningHardSignal {
    MULTI_TENANCY("multi-tenancy"),
    SELF_HOSTED_DEPLOYMENT("self-hosted-deployment"),
    ASYNC_PROCESSING("async-processing"),
    DASHBOARD_AND_API("dashboard-and-api"),
    RBAC_AND_PROVISIONING("rbac-and-provisioning"),
    MULTIPLE_DATASTORES_OR_INTEGRATIONS("multiple-datastores-or-integrations"),
    ANALYTICS_AND_QUERY_SERVING("analytics-and-query-serving"),
    UNKNOWN("unknown");

    private final String value;

    PlanningHardSignal(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PlanningHardSignal fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final PlanningHardSignal signal : values()) {
            if (signal.value.equals(normalizedValue)) {
                return signal;
            }
        }
        return UNKNOWN;
    }
}
