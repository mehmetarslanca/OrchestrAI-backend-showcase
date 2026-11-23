package com.arslanca.orchestrai.business.planning.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.concretes.DeterministicPlanningPolicyManager;
import com.arslanca.orchestrai.business.dtos.orchestration.RequirementCompletenessReport;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

class RequirementCompletenessPolicyTest {

    private final RequirementCompletenessPolicy policy = new RequirementCompletenessPolicy(new DeterministicPlanningPolicyManager());

    @Test
    void shouldFlagEnterpriseScopeMissingTenancyAuthAndOpsSignals() {
        final RequirementCompletenessReport report = policy.evaluateRequirementCompleteness(new StructuredAnalysis(
                "Analytics Platform",
                "saas analytics platform",
                List.of("spring boot", "postgres", "redis"),
                List.of("dashboard", "analytics", "aggregation", "crew provisioning"),
                "complex",
                false,
                "",
                "Enterprise-style analytics platform.",
                List.of("admin", "crew admin", "analyst"),
                List.of("serve multiple organizations"),
                List.of("review insights"),
                List.of("web", "api"),
                true,
                true,
                true,
                "",
                "",
                List.of("postgres", "redis"),
                List.of("sso", "billing"),
                List.of(),
                List.of(),
                "high growth",
                "low latency",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-tenant", "event-driven", "analytics pipeline"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION));

        assertThat(report.ready()).isFalse();
        assertThat(report.unresolvedRequiredSignals()).contains(
                "deployment / hosting model",
                "tenancy model",
                "authentication / authorization model",
                "operational / infrastructure constraints");
    }

    @Test
    void shouldKeepSimpleLandingPageReadyWhenCoreSignalsArePresent() {
        final RequirementCompletenessReport report = policy.evaluateRequirementCompleteness(new StructuredAnalysis(
                "Marketing Site",
                "landing page",
                List.of("nextjs"),
                List.of("hero section", "contact form"),
                "moderate",
                false,
                "",
                "Simple marketing landing page.",
                List.of("visitor"),
                List.of("capture leads"),
                List.of("visit page"),
                List.of("web"),
                true,
                false,
                false,
                "cloud",
                "single-tenant",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "small audience",
                "standard",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION));

        assertThat(report.ready()).isTrue();
        assertThat(report.unresolvedRequiredSignals()).isEmpty();
        assertThat(report.missingRequiredSignals()).isEmpty();
    }
}
