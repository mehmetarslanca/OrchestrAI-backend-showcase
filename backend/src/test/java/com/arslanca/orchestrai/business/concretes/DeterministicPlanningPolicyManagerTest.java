package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

class DeterministicPlanningPolicyManagerTest {

    private final DeterministicPlanningPolicyManager manager = new DeterministicPlanningPolicyManager();

    @Test
    void shouldClassifyEnterpriseAnalyticsFixtureAsEnterpriseProfile() {
        final DeterministicPlanningProfile profile = manager.deriveProfile(enterpriseAnalyticsAnalysis());

        assertThat(profile.effectiveComplexity()).isEqualTo(PlanningComplexity.ENTERPRISE);
        assertThat(profile.hardSignals()).contains(
                PlanningHardSignal.MULTI_TENANCY,
                PlanningHardSignal.SELF_HOSTED_DEPLOYMENT,
                PlanningHardSignal.ASYNC_PROCESSING,
                PlanningHardSignal.DASHBOARD_AND_API,
                PlanningHardSignal.RBAC_AND_PROVISIONING,
                PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS,
                PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING);
        assertThat(profile.shapeEnvelope().minPhaseCount()).isGreaterThanOrEqualTo(8);
        assertThat(profile.shapeEnvelope().requiredCoverageFamilies()).contains(
                PlanningCoverageFamily.TENANCY_AND_ISOLATION,
                PlanningCoverageFamily.IDENTITY_AND_ACCESS,
                PlanningCoverageFamily.OPERATIONS_AND_DEPLOYMENT,
                PlanningCoverageFamily.ASYNC_PROCESSING,
                PlanningCoverageFamily.ANALYTICS_AND_QUERY);
    }

    @Test
    void shouldKeepSimpleLandingPageEnvelopeSmall() {
        final DeterministicPlanningProfile profile = manager.deriveProfile(simpleLandingPageAnalysis());

        assertThat(profile.effectiveComplexity()).isEqualTo(PlanningComplexity.SIMPLE);
        assertThat(profile.shapeEnvelope().maxPhaseCount()).isLessThanOrEqualTo(4);
        assertThat(profile.shapeEnvelope().requiredCoverageFamilies()).containsExactly(PlanningCoverageFamily.FRONTEND_EXPERIENCE);
    }

    @Test
    void shouldNotTreatSingleTenantSaasAsMultiTenant() {
        final DeterministicPlanningProfile profile = manager.deriveProfile(singleTenantWorkspaceAnalysis());

        assertThat(profile.hardSignals()).doesNotContain(PlanningHardSignal.MULTI_TENANCY);
        assertThat(profile.effectiveComplexity()).isEqualTo(PlanningComplexity.MODERATE);
    }

    private StructuredAnalysis enterpriseAnalyticsAnalysis() {
        return new StructuredAnalysis(
                "User Behavior Analytics Platform",
                "saas analytics platform",
                List.of("spring boot", "postgres", "redis", "mongodb"),
                List.of("dashboard", "api", "analytics", "aggregation", "crew provisioning"),
                "complex",
                false,
                "",
                "Multi-tenant self-hosted analytics platform with dashboard and API surfaces.",
                List.of("admin", "crew admin", "analyst"),
                List.of("serve multiple organizations"),
                List.of("ingest events", "review analytics"),
                List.of("web", "api"),
                true,
                true,
                true,
                "self-hosted docker",
                "multi-tenant",
                List.of("postgres", "mongodb", "redis"),
                List.of("sso", "billing"),
                List.of("rbac", "tenant isolation"),
                List.of(),
                "high growth",
                "low latency",
                List.of("local credential management", "customer-managed deployment"),
                List.of(),
                List.of("queue topology"),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-tenant", "event-driven", "analytics pipeline"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private StructuredAnalysis simpleLandingPageAnalysis() {
        return new StructuredAnalysis(
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
                List.of("view landing page"),
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
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private StructuredAnalysis singleTenantWorkspaceAnalysis() {
        return new StructuredAnalysis(
                "Planning Workspace",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "projects"),
                "moderate",
                false,
                "",
                "Internal planning workspace for one team.",
                List.of("admin", "team lead"),
                List.of("coordinate planning"),
                List.of("review blueprint"),
                List.of("web"),
                true,
                true,
                false,
                "cloud",
                "single-tenant",
                List.of("postgres"),
                List.of("github"),
                List.of("oauth"),
                List.of(),
                "moderate growth",
                "interactive",
                List.of("small team"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-role auth"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }
}
