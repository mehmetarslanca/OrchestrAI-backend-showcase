package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

@Service
public class DeterministicPlanningPolicyManager implements DeterministicPlanningPolicyService {

    @Override
    public DeterministicPlanningProfile deriveProfile(final StructuredAnalysis analysis) {
        if (analysis == null) {
            return DeterministicPlanningProfile.unknown();
        }

        final EnumSet<PlanningHardSignal> hardSignals = extractHardSignals(analysis);
        final List<String> decisionReasons = buildDecisionReasons(analysis, hardSignals);
        final PlanningComplexity effectiveComplexity = calculateEffectiveComplexity(analysis, hardSignals, decisionReasons);
        final List<PlanningCoverageFamily> coverageFamilies = extractCoverageFamilies(analysis, hardSignals);
        final PlanningShapeEnvelope shapeEnvelope = buildShapeEnvelope(
                analysis,
                effectiveComplexity,
                coverageFamilies,
                hardSignals,
                decisionReasons);

        return new DeterministicPlanningProfile(
                effectiveComplexity,
                List.copyOf(hardSignals),
                shapeEnvelope,
                decisionReasons);
    }

    private EnumSet<PlanningHardSignal> extractHardSignals(final StructuredAnalysis analysis) {
        final EnumSet<PlanningHardSignal> signals = EnumSet.noneOf(PlanningHardSignal.class);

        if (hasMultiTenancySignals(analysis)) {
            signals.add(PlanningHardSignal.MULTI_TENANCY);
        }
        if (hasSelfHostedSignals(analysis)) {
            signals.add(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT);
        }
        if (hasAsyncProcessingSignals(analysis)) {
            signals.add(PlanningHardSignal.ASYNC_PROCESSING);
        }
        if (hasDashboardAndApiSignals(analysis)) {
            signals.add(PlanningHardSignal.DASHBOARD_AND_API);
        }
        if (hasRbacProvisioningSignals(analysis)) {
            signals.add(PlanningHardSignal.RBAC_AND_PROVISIONING);
        }
        if (hasMultipleDatastoreOrIntegrationSignals(analysis)) {
            signals.add(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS);
        }
        if (hasAnalyticsSignals(analysis)) {
            signals.add(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING);
        }

        return signals;
    }

    private List<PlanningCoverageFamily> extractCoverageFamilies(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals) {
        final EnumSet<PlanningCoverageFamily> families = EnumSet.noneOf(PlanningCoverageFamily.class);

        if (analysis.frontendNeededResolved()) {
            families.add(PlanningCoverageFamily.FRONTEND_EXPERIENCE);
        }
        if (analysis.backendNeededResolved()) {
            families.add(PlanningCoverageFamily.BACKEND_FOUNDATION);
        }
        if (hardSignals.contains(PlanningHardSignal.ASYNC_PROCESSING)) {
            families.add(PlanningCoverageFamily.ASYNC_PROCESSING);
        }
        if (requiresIdentityCoverage(analysis, hardSignals)) {
            families.add(PlanningCoverageFamily.IDENTITY_AND_ACCESS);
        }
        if (hardSignals.contains(PlanningHardSignal.MULTI_TENANCY)) {
            families.add(PlanningCoverageFamily.TENANCY_AND_ISOLATION);
        }
        if (requiresOperationsCoverage(analysis, hardSignals)) {
            families.add(PlanningCoverageFamily.OPERATIONS_AND_DEPLOYMENT);
        }
        if (analysis.backendNeededResolved() || analysis.workerNeededResolved() || !analysis.dataSources().isEmpty()) {
            families.add(PlanningCoverageFamily.DATA_AND_STORAGE);
        }
        if (!analysis.integrationPoints().isEmpty()) {
            families.add(PlanningCoverageFamily.INTEGRATIONS);
        }
        if (hardSignals.contains(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING)) {
            families.add(PlanningCoverageFamily.ANALYTICS_AND_QUERY);
        }

        return List.copyOf(families);
    }

    private PlanningComplexity calculateEffectiveComplexity(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals,
            final List<String> decisionReasons) {
        final PlanningComplexity rawComplexity = analysis.planningComplexity();
        if (rawComplexity == PlanningComplexity.ENTERPRISE) {
            decisionReasons.add("Raw analyzer already classified the scope as enterprise.");
            return PlanningComplexity.ENTERPRISE;
        }

        final int signalScore = scoreSignals(hardSignals, analysis);
        if (isEnterpriseCombination(hardSignals) || signalScore >= 8) {
            decisionReasons.add("Deterministic hard-signal combination elevated planning scope to enterprise.");
            return PlanningComplexity.ENTERPRISE;
        }

        if (rawComplexity == PlanningComplexity.COMPLEX || signalScore >= 4 || requiresComplexCoordination(analysis, hardSignals)) {
            if (rawComplexity != PlanningComplexity.COMPLEX && rawComplexity != PlanningComplexity.ENTERPRISE) {
                decisionReasons.add("Deterministic runtime and integration signals elevated planning scope to complex.");
            }
            return PlanningComplexity.COMPLEX;
        }

        if (shouldDowngradeToSimple(analysis, hardSignals, rawComplexity)) {
            decisionReasons.add("Simple runtime shape and low coordination signals kept the planning envelope small.");
            return PlanningComplexity.SIMPLE;
        }

        return PlanningComplexity.max(rawComplexity, PlanningComplexity.MODERATE);
    }

    private PlanningShapeEnvelope buildShapeEnvelope(
            final StructuredAnalysis analysis,
            final PlanningComplexity complexity,
            final List<PlanningCoverageFamily> families,
            final EnumSet<PlanningHardSignal> signals,
            final List<String> decisionReasons) {
        final int familyCount = families.size();
        final int runtimeComponentCount = runtimeComponentCount(analysis);

        return switch (complexity) {
            case SIMPLE -> new PlanningShapeEnvelope(
                    1,
                    2,
                    Math.max(2, familyCount + 1),
                    4,
                    Math.max(1, runtimeComponentCount),
                    3,
                    families,
                    null,
                    "");
            case MODERATE -> new PlanningShapeEnvelope(
                    Math.max(2, Math.min(3, familyCount)),
                    4,
                    Math.max(4, familyCount + 1),
                    7,
                    Math.max(2, runtimeComponentCount + 1),
                    5,
                    families,
                    null,
                    "");
            case COMPLEX -> new PlanningShapeEnvelope(
                    Math.max(3, Math.min(5, familyCount)),
                    5,
                    Math.max(6, familyCount + 2),
                    10,
                    Math.max(4, runtimeComponentCount + 2),
                    8,
                    families,
                    null,
                    signals.isEmpty()
                            ? ""
                            : "Complex planning signals detected; a stronger planner model may improve output depth.");
            case ENTERPRISE -> new PlanningShapeEnvelope(
                    Math.max(4, Math.min(7, familyCount)),
                    7,
                    Math.max(8, familyCount + 3),
                    12,
                    Math.max(5, runtimeComponentCount + 3),
                    10,
                    families,
                    null,
                    buildEnterpriseWarningReason(signals, decisionReasons));
        };
    }

    private List<String> buildDecisionReasons(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals) {
        final LinkedHashSet<String> decisionReasons = new LinkedHashSet<>();
        if (hardSignals.contains(PlanningHardSignal.MULTI_TENANCY)) {
            decisionReasons.add("Detected multi-tenancy or organization isolation requirements.");
        }
        if (hardSignals.contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)) {
            decisionReasons.add("Detected self-hosted or customer-managed deployment constraints.");
        }
        if (hardSignals.contains(PlanningHardSignal.ASYNC_PROCESSING)) {
            decisionReasons.add("Detected async, worker, queue, or event-driven processing needs.");
        }
        if (hardSignals.contains(PlanningHardSignal.DASHBOARD_AND_API)) {
            decisionReasons.add("Detected both dashboard/frontend and API/backend delivery expectations.");
        }
        if (hardSignals.contains(PlanningHardSignal.RBAC_AND_PROVISIONING)) {
            decisionReasons.add("Detected RBAC, delegated admin, or crew provisioning requirements.");
        }
        if (hardSignals.contains(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS)) {
            decisionReasons.add("Detected multiple datastores or external integration coordination.");
        }
        if (hardSignals.contains(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING)) {
            decisionReasons.add("Detected analytics, aggregation, or query-serving requirements.");
        }
        if (requiresOperationalRigor(analysis)) {
            decisionReasons.add("Scale, latency, or compliance expectations increase planning rigor.");
        }
        return new ArrayList<>(decisionReasons);
    }

    private boolean hasMultiTenancySignals(final StructuredAnalysis analysis) {
        final boolean explicitlySingleTenant = containsAny(analysis.tenancyModel(), "single-tenant", "single tenant");
        return containsAny(analysis.tenancyModel(), "multi-tenant", "multi tenant", "tenant isolation", "org isolation", "organization isolation", "workspace isolation")
                || containsAnyInLists(List.of(analysis.complexityMarkers(), analysis.securityRequirements()),
                        "multi-tenant",
                        "tenant isolation",
                        "org isolation",
                        "workspace isolation")
                || (!explicitlySingleTenant && containsAnyInLists(List.of(analysis.features(), analysis.businessGoals()),
                        "organization management",
                        "workspace provisioning",
                        "b2b onboarding",
                        "multiple organizations"));
    }

    private boolean hasSelfHostedSignals(final StructuredAnalysis analysis) {
        return containsAny(analysis.deploymentModel(),
                "self-hosted",
                "self hosted",
                "on-prem",
                "on premise",
                "air-gapped",
                "customer-managed",
                "private deployment")
                || containsAnyInLists(List.of(analysis.operationalConstraints(), analysis.complexityMarkers()),
                        "self-hosted",
                        "on-prem",
                        "local credential management",
                        "customer-managed",
                        "private deployment");
    }

    private boolean hasAsyncProcessingSignals(final StructuredAnalysis analysis) {
        return analysis.workerNeededResolved()
                || containsAnyInLists(List.of(analysis.features(), analysis.integrationPoints(), analysis.complexityMarkers()),
                        "async",
                        "event-driven",
                        "event driven",
                        "queue",
                        "stream",
                        "background",
                        "worker",
                        "consumer",
                        "ingestion");
    }

    private boolean hasDashboardAndApiSignals(final StructuredAnalysis analysis) {
        return analysis.frontendNeededResolved()
                && analysis.backendNeededResolved()
                && (containsAny(analysis.projectType(), "dashboard", "saas", "web")
                        || containsAnyInLists(List.of(analysis.features(), analysis.deliveryChannels()), "dashboard", "web", "api"));
    }

    private boolean hasRbacProvisioningSignals(final StructuredAnalysis analysis) {
        return containsAnyInLists(List.of(analysis.securityRequirements(), analysis.features(), analysis.complexityMarkers(), analysis.actors()),
                "rbac",
                "role-based access",
                "permission",
                "delegated admin",
                "provisioning",
                "crew",
                "workspace admin",
                "organization admin");
    }

    private boolean hasMultipleDatastoreOrIntegrationSignals(final StructuredAnalysis analysis) {
        return analysis.dataSources().size() > 1
                || analysis.integrationPoints().size() > 1
                || (!analysis.dataSources().isEmpty() && !analysis.integrationPoints().isEmpty());
    }

    private boolean hasAnalyticsSignals(final StructuredAnalysis analysis) {
        return containsAnyInLists(List.of(analysis.features(), analysis.businessGoals(), analysis.complexityMarkers()),
                "analytics",
                "aggregation",
                "reporting",
                "query serving",
                "metrics",
                "insights",
                "clickstream");
    }

    private boolean requiresIdentityCoverage(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals) {
        return hardSignals.contains(PlanningHardSignal.RBAC_AND_PROVISIONING)
                || containsAnyInLists(List.of(analysis.features(), analysis.securityRequirements()),
                        "auth",
                        "login",
                        "oauth",
                        "sso",
                        "admin");
    }

    private boolean requiresOperationsCoverage(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals) {
        return hardSignals.contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)
                || containsAnyInLists(List.of(analysis.operationalConstraints(), analysis.complianceRequirements()),
                        "docker",
                        "kubernetes",
                        "runbook",
                        "observability",
                        "soc2",
                        "gdpr");
    }

    private int scoreSignals(
            final EnumSet<PlanningHardSignal> hardSignals,
            final StructuredAnalysis analysis) {
        int score = 0;
        if (hardSignals.contains(PlanningHardSignal.MULTI_TENANCY)) {
            score += 3;
        }
        if (hardSignals.contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)) {
            score += 3;
        }
        if (hardSignals.contains(PlanningHardSignal.ASYNC_PROCESSING)) {
            score += 2;
        }
        if (hardSignals.contains(PlanningHardSignal.DASHBOARD_AND_API)) {
            score += 1;
        }
        if (hardSignals.contains(PlanningHardSignal.RBAC_AND_PROVISIONING)) {
            score += 2;
        }
        if (hardSignals.contains(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS)) {
            score += 2;
        }
        if (hardSignals.contains(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING)) {
            score += 2;
        }
        if (requiresOperationalRigor(analysis)) {
            score += 1;
        }
        return score;
    }

    private boolean isEnterpriseCombination(final EnumSet<PlanningHardSignal> hardSignals) {
        final boolean multiTenant = hardSignals.contains(PlanningHardSignal.MULTI_TENANCY);
        final boolean selfHosted = hardSignals.contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT);
        final boolean asyncProcessing = hardSignals.contains(PlanningHardSignal.ASYNC_PROCESSING);
        final boolean analytics = hardSignals.contains(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING);
        final boolean rbac = hardSignals.contains(PlanningHardSignal.RBAC_AND_PROVISIONING);
        final boolean multiDatastore = hardSignals.contains(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS);
        return (multiTenant && rbac)
                || (multiTenant && selfHosted)
                || (multiTenant && asyncProcessing && analytics)
                || (asyncProcessing && analytics && multiDatastore && hardSignals.contains(PlanningHardSignal.DASHBOARD_AND_API));
    }

    private boolean requiresComplexCoordination(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals) {
        return runtimeComponentCount(analysis) >= 3
                && (hardSignals.contains(PlanningHardSignal.ASYNC_PROCESSING)
                        || hardSignals.contains(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS)
                        || hardSignals.contains(PlanningHardSignal.RBAC_AND_PROVISIONING));
    }

    private boolean shouldDowngradeToSimple(
            final StructuredAnalysis analysis,
            final EnumSet<PlanningHardSignal> hardSignals,
            final PlanningComplexity rawComplexity) {
        return rawComplexity != PlanningComplexity.COMPLEX
                && rawComplexity != PlanningComplexity.ENTERPRISE
                && hardSignals.isEmpty()
                && runtimeComponentCount(analysis) <= 1
                && !analysis.backendNeededResolved()
                && analysis.integrationPoints().isEmpty()
                && analysis.dataSources().size() <= 1
                && analysis.securityRequirements().isEmpty();
    }

    private boolean requiresOperationalRigor(final StructuredAnalysis analysis) {
        return containsAny(analysis.scaleExpectations(), "high", "large", "global", "enterprise", "real-time")
                || containsAny(analysis.latencyExpectations(), "low latency", "real-time", "sub-second")
                || !analysis.complianceRequirements().isEmpty();
    }

    private int runtimeComponentCount(final StructuredAnalysis analysis) {
        int runtimeComponentCount = 0;
        if (analysis.frontendNeededResolved()) {
            runtimeComponentCount++;
        }
        if (analysis.backendNeededResolved()) {
            runtimeComponentCount++;
        }
        if (analysis.workerNeededResolved()) {
            runtimeComponentCount++;
        }
        return runtimeComponentCount;
    }

    private String buildEnterpriseWarningReason(
            final EnumSet<PlanningHardSignal> signals,
            final List<String> decisionReasons) {
        if (signals.isEmpty()) {
            return "Enterprise scope requires a stronger planner model and broader output shape.";
        }
        return "Enterprise scope detected from %s. A stronger planner model is recommended."
                .formatted(String.join(", ", signals.stream().map(PlanningHardSignal::value).toList()));
    }

    private boolean containsAny(final String value, final String... tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final String normalizedValue = value.toLowerCase(Locale.ROOT);
        for (final String token : tokens) {
            if (normalizedValue.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyInLists(final List<List<String>> values, final String... tokens) {
        return values.stream().anyMatch(value -> containsAnyInList(value, tokens));
    }

    private boolean containsAnyInList(final List<String> values, final String... tokens) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(value -> containsAny(value, tokens));
    }
}
