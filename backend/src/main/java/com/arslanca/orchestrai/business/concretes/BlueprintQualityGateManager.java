package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningActualShape;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFinding;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingCode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualityFindingType;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeAssessment;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateResult;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityGateThresholdProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityScoreCategory;
import com.arslanca.orchestrai.business.dtos.orchestration.QualityVerdict;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalAgent;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalPhase;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;

@Service
public class BlueprintQualityGateManager {

    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;

    public BlueprintQualityGateManager(final DeterministicPlanningPolicyService deterministicPlanningPolicyService) {
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
    }

    public QualityGateResult evaluate(
            final CanonicalBlueprint plan,
            final StructuredAnalysis analysis,
            final PlanningModelPolicy planningModelPolicy) {
        final DeterministicPlanningProfile profile = deterministicPlanningPolicyService.deriveProfile(analysis);
        return evaluate(plan, analysis, planningModelPolicy, profile);
    }

    public QualityGateResult evaluate(
            final CanonicalBlueprint plan,
            final StructuredAnalysis analysis,
            final PlanningModelPolicy planningModelPolicy,
            final DeterministicPlanningProfile planningProfile) {
        final LinkedHashSet<String> findings = new LinkedHashSet<>();
        final List<PlanningQualityFinding> deterministicFindings = new ArrayList<>();
        final QualityGateThresholdProfile thresholdProfile = QualityGateThresholdProfile.forProfile(planningProfile);
        final Map<QualityScoreCategory, Integer> categoryScores = new EnumMap<>(QualityScoreCategory.class);
        final PlanningShapeAssessment shapeAssessment = assessShape(plan, thresholdProfile.shapeEnvelope());

        final int structuralCompleteness = scoreStructuralCompleteness(plan, findings);
        final int scopeCoverage = scoreScopeCoverage(shapeAssessment, findings, deterministicFindings, analysis);
        final int phaseDepth = scorePhaseDepth(plan, findings);
        final int riskCoverage = scoreRiskCoverage(plan, findings);
        final int modelCompatibility = scoreModelCompatibility(thresholdProfile.effectiveComplexity(), planningModelPolicy);
        final int costEfficiencySignal = scoreCostEfficiencySignal(thresholdProfile.effectiveComplexity(), planningModelPolicy);

        registerShapeFindings(shapeAssessment, findings, deterministicFindings);
        registerStarterAndRulesFindings(plan, findings, deterministicFindings);

        categoryScores.put(QualityScoreCategory.STRUCTURAL_COMPLETENESS, structuralCompleteness);
        categoryScores.put(QualityScoreCategory.SCOPE_COVERAGE, scopeCoverage);
        categoryScores.put(QualityScoreCategory.PHASE_DEPTH, phaseDepth);
        categoryScores.put(QualityScoreCategory.RISK_TEST_SECURITY_COVERAGE, riskCoverage);
        categoryScores.put(QualityScoreCategory.MODEL_COMPATIBILITY, modelCompatibility);
        categoryScores.put(QualityScoreCategory.COST_EFFICIENCY_SIGNAL, costEfficiencySignal);

        final int totalScore = structuralCompleteness
                + scopeCoverage
                + phaseDepth
                + riskCoverage
                + modelCompatibility
                + costEfficiencySignal;
        registerDepthAndRiskFindings(
                thresholdProfile,
                phaseDepth,
                riskCoverage,
                findings,
                deterministicFindings);

        final int passingScore = thresholdProfile.passingScore();
        final boolean hasBlockingShapeFinding = hasFindingType(
                deterministicFindings,
                PlanningQualityFindingType.SHAPE_DEFICIT,
                PlanningQualityFindingType.COVERAGE_DEFICIT,
                PlanningQualityFindingType.STARTER_CONFLICT);
        final boolean passed = totalScore >= passingScore
                && structuralCompleteness >= thresholdProfile.minimumStructuralCompleteness()
                && scopeCoverage >= thresholdProfile.minimumScopeCoverage()
                && phaseDepth >= thresholdProfile.minimumPhaseDepth()
                && riskCoverage >= thresholdProfile.minimumRiskCoverage()
                && !hasBlockingShapeFinding
                && !hasFindingType(
                        deterministicFindings,
                        PlanningQualityFindingType.AGENT_DEFICIT,
                        PlanningQualityFindingType.RULES_DEFICIT);

        final QualityVerdict verdict;
        if (passed) {
            verdict = QualityVerdict.ACCEPT;
        } else if (hasBlockingShapeFinding
                || structuralCompleteness < thresholdProfile.minimumStructuralCompleteness() - 2
                || totalScore + thresholdProfile.regenerateScoreGap() < passingScore) {
            verdict = QualityVerdict.FULL_REGENERATE;
        } else {
            verdict = QualityVerdict.TARGETED_EXPANSION;
        }

        return new QualityGateResult(
                verdict,
                passed,
                totalScore,
                passingScore,
                toLegacyCategoryScores(categoryScores),
                List.copyOf(findings),
                deterministicFindings,
                shapeAssessment);
    }

    private int scoreStructuralCompleteness(final CanonicalBlueprint plan, final LinkedHashSet<String> findings) {
        int score = 0;

        if (!plan.executiveSummary().isBlank()) {
            score += 4;
        } else {
            findings.add("Missing executive summary.");
        }
        if (!plan.architectureOverview().isBlank()) {
            score += 5;
        } else {
            findings.add("Missing architecture overview.");
        }
        if (!plan.systemContext().isBlank()) {
            score += 2;
        } else {
            findings.add("Missing system context.");
        }
        if (!plan.majorArchitectureDecisions().isEmpty()) {
            score += 3;
        } else {
            findings.add("Missing major architecture decisions.");
        }
        if (!plan.workstreams().isEmpty()) {
            score += 5;
        } else {
            findings.add("Missing workstreams.");
        }
        if (!plan.phases().isEmpty() && phasesSequential(plan.phases())) {
            score += 3;
        } else if (plan.phases().isEmpty()) {
            findings.add("Missing phases.");
        } else {
            findings.add("Phase ordering is inconsistent.");
        }
        if (!plan.agents().isEmpty()) {
            score += 2;
        } else {
            findings.add("Missing agent set.");
        }
        if (!plan.ideRules().isBlank()) {
            score += 1;
        } else {
            findings.add("Missing IDE rules.");
        }

        return score;
    }

    private int scoreScopeCoverage(
            final PlanningShapeAssessment shapeAssessment,
            final LinkedHashSet<String> findings,
            final List<PlanningQualityFinding> deterministicFindings,
            final StructuredAnalysis analysis) {
        final List<PlanningCoverageFamily> expectedCoverage = shapeAssessment.expectedShape().requiredCoverageFamilies();
        if (expectedCoverage.isEmpty()) {
            return 20;
        }

        final int matched = expectedCoverage.size() - shapeAssessment.missingCoverageFamilies().size();
        for (final PlanningCoverageFamily family : shapeAssessment.missingCoverageFamilies()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.MISSING_REQUIRED_WORKSTREAM_FAMILY,
                    PlanningQualityFindingType.COVERAGE_DEFICIT,
                    "Missing required planning coverage family: %s.".formatted(family.value()));
        }

        if (analysis != null
                && ((analysis.frontendNeededResolved() && shapeAssessment.missingCoverageFamilies().contains(PlanningCoverageFamily.FRONTEND_EXPERIENCE))
                        || (analysis.workerNeededResolved() && shapeAssessment.missingCoverageFamilies().contains(PlanningCoverageFamily.ASYNC_PROCESSING)))) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.MISSING_FRONTEND_OR_WORKER_COVERAGE,
                    PlanningQualityFindingType.COVERAGE_DEFICIT,
                    "Missing frontend or worker coverage required by the deterministic planning shape.");
        }

        return matched * 20 / expectedCoverage.size();
    }

    private int scorePhaseDepth(final CanonicalBlueprint plan, final LinkedHashSet<String> findings) {
        if (plan.phases().isEmpty()) {
            return 0;
        }

        int phasePoints = 0;
        for (final CanonicalPhase phase : plan.phases()) {
            int currentPhasePoints = 0;
            if (phase.objective().length() >= 20) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing a meaningful objective.".formatted(phase.order()));
            }
            if (!phase.whyThisPhaseExists().isBlank()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing rationale.".formatted(phase.order()));
            }
            if (!phase.deliverables().isEmpty()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing deliverables.".formatted(phase.order()));
            }
            if (!phase.acceptanceCriteria().isEmpty()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing acceptance criteria.".formatted(phase.order()));
            }
            if (!phase.contracts().isEmpty() || !phase.scopeIn().isEmpty()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing scope/contracts detail.".formatted(phase.order()));
            }
            phasePoints += currentPhasePoints;
        }

        return phasePoints * 20 / (plan.phases().size() * 5);
    }

    private int scoreRiskCoverage(final CanonicalBlueprint plan, final LinkedHashSet<String> findings) {
        if (plan.phases().isEmpty()) {
            return 0;
        }

        int phasePoints = 0;
        for (final CanonicalPhase phase : plan.phases()) {
            int currentPhasePoints = 0;
            if (!phase.testingStrategy().isBlank()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing testing strategy.".formatted(phase.order()));
            }
            if (!phase.securityConsiderations().isEmpty()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing security considerations.".formatted(phase.order()));
            }
            if (!phase.observabilityPlan().isBlank()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing observability plan.".formatted(phase.order()));
            }
            if (!phase.risks().isEmpty()) {
                currentPhasePoints++;
            } else {
                findings.add("Phase %d is missing risks.".formatted(phase.order()));
            }
            phasePoints += currentPhasePoints;
        }

        return phasePoints * 20 / (plan.phases().size() * 4);
    }

    private int scoreModelCompatibility(
            final com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity effectiveComplexity,
            final PlanningModelPolicy planningModelPolicy) {
        if (planningModelPolicy == null) {
            return CapabilityRisk.UNKNOWN.modelCompatibilityScore();
        }

        int score = planningModelPolicy.capabilityRisk().modelCompatibilityScore();

        if (effectiveComplexity.requiresPreviewReview()
                && planningModelPolicy.selectedPlanningMode() == PlanningMode.SINGLE_PASS) {
            score = Math.min(score, 2);
        }

        if (effectiveComplexity == com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.ENTERPRISE
                && planningModelPolicy.selectedPlanningMode() != PlanningMode.MULTI_PASS_EXPANSION) {
            return 0;
        }

        return score;
    }

    private int scoreCostEfficiencySignal(
            final com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity effectiveComplexity,
            final PlanningModelPolicy planningModelPolicy) {
        if (planningModelPolicy == null) {
            return 2;
        }

        final PlanningMode planningMode = planningModelPolicy.selectedPlanningMode();

        if (effectiveComplexity == com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.SIMPLE
                && planningMode == PlanningMode.SINGLE_PASS) {
            return 5;
        }
        if (effectiveComplexity == com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity.MODERATE
                && planningMode != PlanningMode.MULTI_PASS_EXPANSION) {
            return 5;
        }
        if (effectiveComplexity.requiresPreviewReview() && planningMode == PlanningMode.MULTI_PASS_EXPANSION) {
            return 5;
        }
        return 2;
    }

    private boolean phasesSequential(final List<CanonicalPhase> phases) {
        int expectedOrder = 1;
        for (final CanonicalPhase phase : phases) {
            if (phase.order() != expectedOrder) {
                return false;
            }
            expectedOrder++;
        }
        return true;
    }

    private PlanningShapeAssessment assessShape(
            final CanonicalBlueprint plan,
            final PlanningShapeEnvelope expectedShape) {
        final PlanningShapeEnvelope normalizedExpectedShape = expectedShape == null ? PlanningShapeEnvelope.unknown() : expectedShape;
        final PlanningActualShape actualShape = new PlanningActualShape(
                plan.workstreams().size(),
                plan.phases().size(),
                plan.agents().size(),
                representedCoverageFamilies(plan),
                workstreamsWithoutPhases(plan));
        final List<PlanningCoverageFamily> missingCoverageFamilies = normalizedExpectedShape.requiredCoverageFamilies().stream()
                .filter(family -> !actualShape.coveredFamilies().contains(family))
                .toList();

        return new PlanningShapeAssessment(
                normalizedExpectedShape,
                actualShape,
                actualShape.workstreamCount() >= normalizedExpectedShape.minWorkstreamCount(),
                actualShape.phaseCount() >= normalizedExpectedShape.minPhaseCount(),
                actualShape.agentCount() >= normalizedExpectedShape.minAgentCount(),
                missingCoverageFamilies);
    }

    private void registerShapeFindings(
            final PlanningShapeAssessment shapeAssessment,
            final LinkedHashSet<String> findings,
            final List<PlanningQualityFinding> deterministicFindings) {
        if (!shapeAssessment.workstreamCountSatisfied()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.UNDER_MIN_WORKSTREAM_COUNT,
                    PlanningQualityFindingType.SHAPE_DEFICIT,
                    "Workstream count is below deterministic minimum: expected at least %d but found %d."
                            .formatted(
                                    shapeAssessment.expectedShape().minWorkstreamCount(),
                                    shapeAssessment.actualShape().workstreamCount()));
        }
        if (!shapeAssessment.phaseCountSatisfied()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.UNDER_MIN_PHASE_COUNT,
                    PlanningQualityFindingType.SHAPE_DEFICIT,
                    "Phase count is below deterministic minimum: expected at least %d but found %d."
                            .formatted(
                                    shapeAssessment.expectedShape().minPhaseCount(),
                                    shapeAssessment.actualShape().phaseCount()));
        }
        if (!shapeAssessment.agentCountSatisfied()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.UNDER_MIN_AGENT_COUNT,
                    PlanningQualityFindingType.AGENT_DEFICIT,
                    "Agent count is below deterministic minimum: expected at least %d but found %d."
                            .formatted(
                                    shapeAssessment.expectedShape().minAgentCount(),
                                    shapeAssessment.actualShape().agentCount()));
        }
        for (final String workstreamTitle : shapeAssessment.actualShape().workstreamsWithoutPhases()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.WORKSTREAM_WITHOUT_PHASE,
                    PlanningQualityFindingType.SHAPE_DEFICIT,
                    "Workstream '%s' does not contain any phases.".formatted(workstreamTitle));
        }
    }

    private void registerStarterAndRulesFindings(
            final CanonicalBlueprint plan,
            final LinkedHashSet<String> findings,
            final List<PlanningQualityFinding> deterministicFindings) {
        if (hasStarterArchitectureConflict(plan)) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.STARTER_ARCHITECTURE_CONFLICT,
                    PlanningQualityFindingType.STARTER_CONFLICT,
                    "Starter notes indicate an architecture conflict that requires full regeneration.");
        }
        if (plan.ideRules().isBlank()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.IDE_RULES_CONFLICT,
                    PlanningQualityFindingType.RULES_DEFICIT,
                    "IDE rules are missing and require dedicated rules repair.");
        }
    }

    private void registerDepthAndRiskFindings(
            final QualityGateThresholdProfile thresholdProfile,
            final int phaseDepth,
            final int riskCoverage,
            final LinkedHashSet<String> findings,
            final List<PlanningQualityFinding> deterministicFindings) {
        if (phaseDepth < thresholdProfile.minimumPhaseDepth()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.WEAK_PHASE_DEPTH,
                    PlanningQualityFindingType.DEPTH_DEFICIT,
                    "Phase depth is below the deterministic minimum threshold.");
        }
        if (riskCoverage < thresholdProfile.minimumRiskCoverage()) {
            addFinding(
                    findings,
                    deterministicFindings,
                    PlanningQualityFindingCode.WEAK_RISK_COVERAGE,
                    PlanningQualityFindingType.RISK_DEFICIT,
                    "Risk, testing, or security coverage is below the deterministic minimum threshold.");
        }
    }

    private List<String> workstreamsWithoutPhases(final CanonicalBlueprint plan) {
        final List<String> workstreamsWithoutPhases = new ArrayList<>();
        for (final CanonicalWorkstream workstream : plan.workstreams()) {
            if (workstream.phases().isEmpty()) {
                workstreamsWithoutPhases.add(workstream.title().isBlank() ? "unnamed-workstream" : workstream.title());
            }
        }
        return List.copyOf(workstreamsWithoutPhases);
    }

    private boolean hasStarterArchitectureConflict(final CanonicalBlueprint plan) {
        final String starterCorpus = String.join(" ", plan.starterNotes(), plan.architectureOverview(), plan.systemContext())
                .toLowerCase(Locale.ROOT);
        return containsAny(
                starterCorpus,
                "starter conflict",
                "starter mismatch",
                "incompatible starter",
                "starter is incompatible",
                "architecture conflict");
    }

    private List<PlanningCoverageFamily> representedCoverageFamilies(final CanonicalBlueprint plan) {
        final List<PlanningCoverageFamily> workstreamFamilies = PlanningCoverageFamily.matchedBy(
                buildWorkstreamCoverageCorpus(plan.workstreams()));
        final List<PlanningCoverageFamily> phaseFamilies = PlanningCoverageFamily.matchedBy(
                buildPhaseCoverageCorpus(plan.phases()));
        final LinkedHashSet<PlanningCoverageFamily> representedFamilies = new LinkedHashSet<>();
        for (final PlanningCoverageFamily family : workstreamFamilies) {
            if (phaseFamilies.contains(family)) {
                representedFamilies.add(family);
            }
        }
        return List.copyOf(representedFamilies);
    }

    private String buildWorkstreamCoverageCorpus(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return "";
        }

        final List<String> fragments = new ArrayList<>();
        fragments.addAll(workstreams.stream().map(CanonicalWorkstream::title).toList());
        fragments.addAll(workstreams.stream().map(CanonicalWorkstream::goal).toList());
        fragments.addAll(workstreams.stream().map(CanonicalWorkstream::whyItExists).toList());
        fragments.addAll(workstreams.stream().flatMap(workstream -> workstream.includedScope().stream()).toList());
        return String.join(" ", fragments).toLowerCase(Locale.ROOT);
    }

    private String buildPhaseCoverageCorpus(final List<CanonicalPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return "";
        }

        final List<String> fragments = new ArrayList<>();
        fragments.addAll(phases.stream().map(CanonicalPhase::title).toList());
        fragments.addAll(phases.stream().map(CanonicalPhase::objective).toList());
        fragments.addAll(phases.stream().map(CanonicalPhase::whyThisPhaseExists).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.scopeIn().stream()).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.deliverables().stream()).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.contracts().stream()).toList());
        fragments.addAll(phases.stream().map(CanonicalPhase::dataImpact).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.securityConsiderations().stream()).toList());
        fragments.addAll(phases.stream().map(CanonicalPhase::observabilityPlan).toList());
        fragments.addAll(phases.stream().map(CanonicalPhase::testingStrategy).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.acceptanceCriteria().stream()).toList());
        fragments.addAll(phases.stream().flatMap(phase -> phase.risks().stream()).toList());
        return String.join(" ", fragments).toLowerCase(Locale.ROOT);
    }

    private String buildCorpus(final CanonicalBlueprint plan) {
        final List<String> fragments = new ArrayList<>();
        fragments.add(plan.executiveSummary());
        fragments.add(plan.architectureOverview());
        fragments.add(plan.systemContext());
        fragments.addAll(plan.majorArchitectureDecisions());
        fragments.addAll(plan.nonFunctionalRequirements());
        fragments.addAll(plan.crossCuttingConcerns());
        fragments.addAll(plan.assumptions());
        fragments.addAll(plan.unknowns());
        fragments.addAll(plan.workstreams().stream().map(CanonicalWorkstream::title).toList());
        fragments.addAll(plan.workstreams().stream().map(CanonicalWorkstream::goal).toList());
        fragments.addAll(plan.workstreams().stream().map(CanonicalWorkstream::whyItExists).toList());
        fragments.addAll(plan.workstreams().stream().flatMap(workstream -> workstream.includedScope().stream()).toList());
        fragments.addAll(plan.phases().stream().map(CanonicalPhase::title).toList());
        fragments.addAll(plan.phases().stream().map(CanonicalPhase::objective).toList());
        fragments.addAll(plan.phases().stream().map(CanonicalPhase::whyThisPhaseExists).toList());
        fragments.addAll(plan.phases().stream().flatMap(phase -> phase.deliverables().stream()).toList());
        fragments.addAll(plan.agents().stream().map(CanonicalAgent::role).toList());
        fragments.addAll(plan.agents().stream().flatMap(agent -> agent.responsibilities().stream()).toList());
        return String.join(" ", fragments).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(final String value, final String... tokens) {
        final String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (final String token : tokens) {
            if (normalizedValue.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(final List<String> values, final String... tokens) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(value -> containsAny(value, tokens));
    }

    private void addFinding(
            final LinkedHashSet<String> findings,
            final List<PlanningQualityFinding> deterministicFindings,
            final PlanningQualityFindingCode code,
            final PlanningQualityFindingType type,
            final String message) {
        findings.add(message);
        deterministicFindings.add(new PlanningQualityFinding(code, type, message));
    }

    private boolean hasFindingType(
            final List<PlanningQualityFinding> deterministicFindings,
            final PlanningQualityFindingType... findingTypes) {
        for (final PlanningQualityFinding finding : deterministicFindings) {
            for (final PlanningQualityFindingType findingType : findingTypes) {
                if (finding.type() == findingType) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Integer> toLegacyCategoryScores(final Map<QualityScoreCategory, Integer> categoryScores) {
        final Map<String, Integer> legacyCategoryScores = new LinkedHashMap<>();
        for (final Map.Entry<QualityScoreCategory, Integer> entry : categoryScores.entrySet()) {
            legacyCategoryScores.put(entry.getKey().value(), entry.getValue());
        }
        return Map.copyOf(legacyCategoryScores);
    }
}
