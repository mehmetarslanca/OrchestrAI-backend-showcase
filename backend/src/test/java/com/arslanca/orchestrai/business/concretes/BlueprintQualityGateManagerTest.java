package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingCode;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

class BlueprintQualityGateManagerTest {

    private final BlueprintQualityGateManager manager = new BlueprintQualityGateManager(new DeterministicPlanningPolicyManager());
    private final BlueprintPlanCompatibilityAdapter blueprintPlanCompatibilityAdapter = new BlueprintPlanCompatibilityAdapter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptSmallSimplePlanWhenDeterministicShapeIsSatisfied() {
        final QualityGateResult result = manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(simpleFrontendPlan()),
                simpleFrontendAnalysis(),
                simplePolicy());

        assertThat(result.passed()).isTrue();
        assertThat(result.verdict()).isEqualTo(QualityVerdict.ACCEPT);
        assertThat(result.shapeAssessment().workstreamCountSatisfied()).isTrue();
        assertThat(result.shapeAssessment().phaseCountSatisfied()).isTrue();
        assertThat(result.shapeAssessment().agentCountSatisfied()).isTrue();
        assertThat(result.shapeAssessment().missingCoverageFamilies()).isEmpty();
    }

    @Test
    void shouldRequestTargetedExpansionForDepthOnlyWeakness() {
        final QualityGateResult result = manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(shallowFrontendPlan()),
                simpleFrontendAnalysis(),
                simplePolicy());

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(QualityVerdict.TARGETED_EXPANSION);
        assertThat(result.shapeAssessment().hasShapeDeficit()).isFalse();
        assertThat(result.deterministicFindings())
                .extracting(finding -> finding.code())
                .contains(PlanningQualityFindingCode.WEAK_PHASE_DEPTH, PlanningQualityFindingCode.WEAK_RISK_COVERAGE);
    }

    @Test
    void shouldFailEnterpriseLikeTwoPhasePlanDeterministically() {
        final QualityGateResult result = manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(enterpriseLikeTwoPhasePlan()),
                enterpriseLikeAnalysis(),
                complexPolicy());

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(QualityVerdict.FULL_REGENERATE);
        assertThat(result.shapeAssessment().expectedShape().minPhaseCount()).isGreaterThanOrEqualTo(8);
        assertThat(result.shapeAssessment().phaseCountSatisfied()).isFalse();
        assertThat(result.deterministicFindings())
                .extracting(finding -> finding.code())
                .contains(PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT);
    }

    @Test
    void shouldFailWhenRequiredCoverageFamilyIsMissing() {
        final QualityGateResult result = manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(missingFrontendCoveragePlan()),
                simpleFrontendAnalysis(),
                simplePolicy());

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(QualityVerdict.FULL_REGENERATE);
        assertThat(result.shapeAssessment().missingCoverageFamilies()).contains(PlanningCoverageFamily.FRONTEND_EXPERIENCE);
        assertThat(result.deterministicFindings())
                .extracting(finding -> finding.code())
                .contains(
                        PlanningQualityFindingCode.MISSING_REQUIRED_WORKSTREAM_FAMILY,
                        PlanningQualityFindingCode.MISSING_FRONTEND_OR_WORKER_COVERAGE);
    }

    @Test
    void shouldLoadPlanningRegressionFixturesAcrossAllComplexityTiers() {
        assertFixtureLoads("simple");
        assertFixtureLoads("moderate");
        assertFixtureLoads("complex");
        assertFixtureLoads("enterprise");
    }

    @Test
    void shouldRejectEnterpriseRegressionFixtureAsShallowTwoPhaseSuccess() {
        final QualityGateResult result = manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(loadFixturePlan("enterprise")),
                loadFixtureAnalysis("enterprise"),
                complexPolicy());

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo(QualityVerdict.FULL_REGENERATE);
        assertThat(result.shapeAssessment().phaseCountSatisfied()).isFalse();
        assertThat(result.shapeAssessment().expectedShape().minPhaseCount()).isGreaterThanOrEqualTo(8);
        assertThat(result.shapeAssessment().actualShape().phaseCount()).isEqualTo(2);
        assertThat(result.deterministicFindings())
                .extracting(finding -> finding.code())
                .contains(PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT);
    }

    private BlueprintPlan simpleFrontendPlan() {
        final BlueprintPlan.PhaseDefinition designPhase = completePhase(
                1,
                "Frontend foundation",
                "Build the frontend shell, routing, and reusable UI primitives for the marketing site.",
                "Needed so the frontend experience has a stable foundation before launch.",
                List.of("frontend shell", "design system", "landing page"),
                List.of("frontend shell delivered", "navigation works", "hero content renders"),
                List.of("session protection", "safe form handling"),
                "browser metrics and error reporting",
                "component tests and smoke coverage",
                List.of("hero renders", "navigation is responsive"));
        final BlueprintPlan.PhaseDefinition launchPhase = completePhase(
                2,
                "Frontend launch",
                "Finalize the frontend checkout, analytics hooks, and content workflow for launch readiness.",
                "Needed to complete the frontend operator journey and release workflow.",
                List.of("checkout page", "frontend analytics", "content workflow"),
                List.of("checkout works", "analytics capture key events", "launch checklist complete"),
                List.of("sensitive links hidden from guests"),
                "page performance tracking and funnel metrics",
                "end-to-end smoke coverage",
                List.of("checkout flow works", "launch checklist signed off"));

        return new BlueprintPlan(
                "Launch Site",
                "Executive summary",
                "A frontend-first delivery plan for a small marketing site.",
                "The team is shipping a simple web presence with no backend orchestration needs.",
                List.of("Keep the frontend implementation lightweight and accessible."),
                List.of(new BlueprintPlan.WorkstreamDefinition(
                        "Frontend experience",
                        "Deliver the landing page and launch workflow",
                        "The product only needs a polished frontend experience.",
                        List.of("frontend ui", "web routing", "dashboard copy review"),
                        List.of("backend services"),
                        List.of(),
                        List.of("frontend-engineer"),
                        List.of(designPhase, launchPhase))),
                List.of("accessibility"),
                List.of("observability"),
                List.of(),
                List.of("Static hosting is acceptable."),
                List.of("Long-term CMS choice remains open."),
                Map.of("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                List.of(designPhase, launchPhase),
                List.of(new BlueprintPlan.AgentDefinition("frontend-engineer", "prompt", List.of("ship frontend experience"))),
                "frontend rules",
                "starter notes");
    }

    private BlueprintPlan shallowFrontendPlan() {
        final BlueprintPlan.PhaseDefinition phaseOne = new BlueprintPlan.PhaseDefinition(
                1,
                "Frontend foundation",
                "Short goal",
                "",
                List.of("frontend shell"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                "handoff",
                "1.0",
                Map.of(),
                "",
                List.of());
        final BlueprintPlan.PhaseDefinition phaseTwo = new BlueprintPlan.PhaseDefinition(
                2,
                "Frontend launch",
                "Short goal",
                "",
                List.of("frontend launch"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                "handoff",
                "1.0",
                Map.of(),
                "",
                List.of());

        return new BlueprintPlan(
                "Launch Site",
                "Executive summary",
                "Frontend plan",
                "Small web experience",
                List.of("Keep a frontend focus."),
                List.of(new BlueprintPlan.WorkstreamDefinition(
                        "Frontend experience",
                        "Deliver the web launch",
                        "Required for the frontend release.",
                        List.of("frontend ui", "web pages"),
                        List.of(),
                        List.of(),
                        List.of("frontend-engineer"),
                        List.of(phaseOne, phaseTwo))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                List.of(phaseOne, phaseTwo),
                List.of(new BlueprintPlan.AgentDefinition("frontend-engineer", "prompt", List.of("ship frontend"))),
                "frontend rules",
                "starter notes");
    }

    private BlueprintPlan enterpriseLikeTwoPhasePlan() {
        final BlueprintPlan.PhaseDefinition phaseOne = completePhase(
                1,
                "Platform foundation",
                "Establish the backend api, tenant model, worker ingestion, and starter observability baseline.",
                "Needed to start the multi-tenant platform safely.",
                List.of("backend api", "tenant isolation", "worker queue", "postgres schema"),
                List.of("tenant-aware api", "queue worker", "deployment baseline"),
                List.of("rbac enforcement", "audit logging"),
                "runbook metrics and platform traces",
                "integration and worker tests",
                List.of("tenant auth works", "worker consumes messages"));
        final BlueprintPlan.PhaseDefinition phaseTwo = completePhase(
                2,
                "Dashboard and analytics",
                "Ship the frontend dashboard, analytics reporting, and billing integration for operators.",
                "Needed to expose the enterprise platform to customers.",
                List.of("frontend dashboard", "stripe integration", "analytics queries"),
                List.of("dashboard released", "reporting available", "billing sync enabled"),
                List.of("session protection", "admin boundaries"),
                "dashboard metrics and billing alerts",
                "end-to-end tenant flows",
                List.of("dashboard loads", "billing sync passes"));

        return new BlueprintPlan(
                "Enterprise Platform",
                "Executive summary",
                "A multi-tenant self-hosted platform with backend, frontend, worker, analytics, and integrations.",
                "Operators manage tenant provisioning, analytics, and worker automation from a dashboard and api.",
                List.of("Use modular services with strict identity and access boundaries."),
                List.of(
                        new BlueprintPlan.WorkstreamDefinition(
                                "Platform core",
                                "Deliver backend api, identity, tenant isolation, worker processing, and operations",
                                "Required for multi-tenant self-hosted delivery.",
                                List.of("backend api", "identity and access", "tenant isolation", "worker queue", "operations runbook", "data storage"),
                                List.of(),
                                List.of(),
                                List.of("backend-engineer", "platform-engineer"),
                                List.of(phaseOne)),
                        new BlueprintPlan.WorkstreamDefinition(
                                "Operator experience",
                                "Deliver dashboard analytics and integrations",
                                "Required for reporting and customer workflows.",
                                List.of("frontend dashboard", "analytics reporting", "stripe integration"),
                                List.of(),
                                List.of("Platform core"),
                                List.of("frontend-engineer", "data-engineer"),
                                List.of(phaseTwo))),
                List.of("availability", "tenant isolation"),
                List.of("security", "observability"),
                List.of("tenant"),
                List.of("Self-hosted deployment is mandatory."),
                List.of("SLA details remain open."),
                Map.of("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                List.of(phaseOne, phaseTwo),
                List.of(
                        new BlueprintPlan.AgentDefinition("backend-engineer", "prompt", List.of("ship backend api")),
                        new BlueprintPlan.AgentDefinition("frontend-engineer", "prompt", List.of("ship dashboard"))),
                "enterprise rules",
                "starter notes");
    }

    private BlueprintPlan missingFrontendCoveragePlan() {
        final BlueprintPlan.PhaseDefinition phaseOne = completePhase(
                1,
                "Foundation",
                "Establish the first delivery phase with reusable content structure and release sequencing.",
                "Needed to align the launch work.",
                List.of("content flow"),
                List.of("first deliverable ready"),
                List.of("safe review workflow"),
                "basic release metrics",
                "review tests",
                List.of("phase one approved"));
        final BlueprintPlan.PhaseDefinition phaseTwo = completePhase(
                2,
                "Launch",
                "Finalize the release pack, review checklist, and stakeholder handoff for the next milestone.",
                "Needed to complete the launch sequencing.",
                List.of("release pack"),
                List.of("launch checklist complete"),
                List.of("handoff permissions reviewed"),
                "checklist telemetry",
                "launch rehearsal tests",
                List.of("launch review complete"));

        return new BlueprintPlan(
                "Launch Site",
                "Executive summary",
                "A small frontend release plan.",
                "A lightweight delivery outline that mentions a frontend launch but does not decompose it.",
                List.of("Keep the plan focused."),
                List.of(new BlueprintPlan.WorkstreamDefinition(
                        "Launch sequencing",
                        "Coordinate release readiness",
                        "Required to align delivery milestones.",
                        List.of("release notes", "checklist ownership"),
                        List.of(),
                        List.of(),
                        List.of("implementer"),
                        List.of(phaseOne, phaseTwo))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                List.of(phaseOne, phaseTwo),
                List.of(new BlueprintPlan.AgentDefinition("implementer", "prompt", List.of("ship launch sequencing"))),
                "general rules",
                "starter notes");
    }

    private BlueprintPlan.PhaseDefinition completePhase(
            final int order,
            final String title,
            final String objective,
            final String why,
            final List<String> deliverables,
            final List<String> acceptanceCriteria,
            final List<String> security,
            final String observability,
            final String testing,
            final List<String> risks) {
        return new BlueprintPlan.PhaseDefinition(
                order,
                title,
                objective,
                why,
                List.of("scope item"),
                List.of("scope out"),
                List.of("starter approved"),
                deliverables,
                List.of("decision"),
                List.of("contract"),
                "data impact",
                security,
                observability,
                testing,
                acceptanceCriteria,
                risks,
                "handoff",
                "1.0",
                Map.of(),
                "",
                List.of());
    }

    private void assertFixtureLoads(final String tier) {
        final StructuredAnalysis analysis = loadFixtureAnalysis(tier);
        final BlueprintPlan plan = loadFixturePlan(tier);

        assertThat(analysis.contractVersion()).isEqualTo(StructuredAnalysis.CURRENT_CONTRACT_VERSION);
        assertThat(plan.contractVersion()).isEqualTo(BlueprintPlan.CURRENT_CONTRACT_VERSION);
        assertThat(manager.evaluate(
                blueprintPlanCompatibilityAdapter.toCanonicalBlueprint(plan),
                analysis,
                policyFor(analysis.planningComplexity()))
                .shapeAssessment())
                .isNotNull();
    }

    private StructuredAnalysis loadFixtureAnalysis(final String tier) {
        return readFixture(tier, "analysis.json", StructuredAnalysis.class);
    }

    private BlueprintPlan loadFixturePlan(final String tier) {
        return readFixture(tier, "blueprint-plan.json", BlueprintPlan.class);
    }

    private <T> T readFixture(final String tier, final String fileName, final Class<T> targetType) {
        final String resourcePath = "/planning-regression-fixtures/" + tier + "/" + fileName;
        try (InputStream inputStream = BlueprintQualityGateManagerTest.class.getResourceAsStream(resourcePath)) {
            assertThat(inputStream).as(resourcePath).isNotNull();
            return objectMapper.readValue(inputStream, targetType);
        } catch (final IOException exception) {
            throw new UncheckedIOException("Failed to read fixture " + resourcePath, exception);
        }
    }

    private PlanningModelPolicy policyFor(final com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity complexity) {
        return switch (complexity) {
            case SIMPLE, MODERATE -> simplePolicy();
            case COMPLEX, ENTERPRISE -> complexPolicy();
        };
    }

    private StructuredAnalysis simpleFrontendAnalysis() {
        return new StructuredAnalysis(
                "Launch Site",
                "marketing-site",
                List.of("nextjs"),
                List.of("landing page", "content updates"),
                "moderate",
                false,
                "",
                "Simple frontend-only site",
                List.of("visitor"),
                List.of("publish a web presence"),
                List.of("read content"),
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
                "small",
                "interactive",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private StructuredAnalysis enterpriseLikeAnalysis() {
        return new StructuredAnalysis(
                "Enterprise Platform",
                "saas",
                List.of("nextjs", "spring boot", "postgres"),
                List.of("auth", "billing", "dashboard", "analytics", "background jobs"),
                "complex",
                false,
                "",
                "Enterprise-like platform",
                List.of("admin"),
                List.of("launch b2b platform"),
                List.of("manage tenants", "view analytics"),
                List.of("web"),
                true,
                true,
                true,
                "self-hosted kubernetes",
                "multi-tenant",
                List.of("postgres", "object storage"),
                List.of("stripe", "crm"),
                List.of("rbac", "sso"),
                List.of("soc2"),
                "high growth",
                "interactive",
                List.of("runbook required", "observability required"),
                List.of("Managed services are acceptable."),
                List.of("Exact data retention window open."),
                List.of("Need clickstream aggregation?"),
                List.of(),
                List.of(),
                List.of("analytics", "worker"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private PlanningModelPolicy simplePolicy() {
        return new PlanningModelPolicy(
                PlanningMode.SINGLE_PASS,
                "low",
                false,
                false,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                "none");
    }

    private PlanningModelPolicy complexPolicy() {
        return new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                "none");
    }
}
