package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;

public final class PlanningDecisionPayloadMapper {

    private PlanningDecisionPayloadMapper() {
    }

    public static Map<String, Object> toLegacyQualitySummary(final PlanningQualitySummary summary) {
        if (summary == null) {
            return Map.of();
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("complexity", summary.complexity().name().toLowerCase());
        payload.put("complexityMarkers", summary.complexityMarkers());
        payload.put("capabilityRisk", summary.capabilityRisk().value());
        payload.put("previewRequired", summary.previewRequired());
        payload.put("previewRecommended", summary.previewRecommended());
        payload.put("resolvedModelKey", summary.plannerModelRef().resolvedModelKey());
        payload.put("modelCapability", summary.plannerModelRef().modelCapability().name().toLowerCase());
        payload.put("localModel", summary.plannerModelRef().localModel());
        payload.put("degradeStrategy", summary.degradeStrategy().value());
        payload.put("docsOnlyFallback", summary.docsOnlyFallback());
        payload.put("qualityScore", summary.qualityScore());
        payload.put("passingScore", summary.passingScore());
        payload.put("qualityVerdict", summary.qualityVerdict().value());
        payload.put("qualityPassed", summary.qualityPassed());
        payload.put("categoryScores", toLegacyCategoryScores(summary.categoryScores()));
        payload.put("effectiveComplexity", summary.effectiveComplexity().name().toLowerCase());
        payload.put("shapeAssessment", toLegacyShapeAssessment(summary.shapeAssessment()));
        payload.put("deterministicFindings", toLegacyDeterministicFindings(summary.deterministicFindings()));
        payload.put("findings", summary.findings());
        payload.put("policyVersionFingerprint", summary.policyVersionFingerprint().value());
        return Map.copyOf(payload);
    }

    public static PlanningQualitySummary fromLegacyQualitySummary(final Map<String, Object> payload) {
        final Map<String, Object> normalizedPayload = payload == null ? Map.of() : payload;
        final PlanningComplexity complexity = PlanningComplexity.fromValue(stringValue(normalizedPayload.get("complexity")));
        final String effectiveComplexityValue = stringValue(normalizedPayload.get("effectiveComplexity"));
        final PlannerModelRef plannerModelRef = new PlannerModelRef(
                stringValue(normalizedPayload.get("resolvedModelKey")),
                modelCapabilityFromValue(normalizedPayload.get("modelCapability")),
                booleanValue(normalizedPayload.get("localModel")));

        return new PlanningQualitySummary(
                complexity,
                stringList(normalizedPayload.get("complexityMarkers")),
                CapabilityRisk.fromValue(stringValue(normalizedPayload.get("capabilityRisk"))),
                booleanValue(normalizedPayload.get("previewRequired")),
                booleanValue(normalizedPayload.get("previewRecommended")),
                plannerModelRef,
                DegradeStrategy.fromValue(stringValue(normalizedPayload.get("degradeStrategy"))),
                booleanValue(normalizedPayload.get("docsOnlyFallback")),
                intValue(normalizedPayload.get("qualityScore")),
                intValue(normalizedPayload.get("passingScore")),
                QualityVerdict.fromValue(stringValue(normalizedPayload.get("qualityVerdict"))),
                booleanValue(normalizedPayload.get("qualityPassed")),
                fromLegacyCategoryScores(normalizedPayload.get("categoryScores")),
                effectiveComplexityValue.isBlank() ? complexity : PlanningComplexity.fromValue(effectiveComplexityValue),
                fromLegacyShapeAssessment(normalizedPayload.get("shapeAssessment")),
                fromLegacyDeterministicFindings(normalizedPayload.get("deterministicFindings")),
                stringList(normalizedPayload.get("findings")),
                new PolicyVersionFingerprint(stringValue(normalizedPayload.get("policyVersionFingerprint"))));
    }

    public static Map<String, Integer> toLegacyCategoryScores(final Map<QualityScoreCategory, Integer> categoryScores) {
        if (categoryScores == null || categoryScores.isEmpty()) {
            return Map.of();
        }

        final Map<String, Integer> payload = new LinkedHashMap<>();
        for (final Map.Entry<QualityScoreCategory, Integer> entry : categoryScores.entrySet()) {
            payload.put(entry.getKey().value(), entry.getValue());
        }
        return Map.copyOf(payload);
    }

    public static Map<QualityScoreCategory, Integer> fromLegacyCategoryScores(final Object categoryScores) {
        if (!(categoryScores instanceof Map<?, ?> payload)) {
            return Map.of();
        }

        final Map<QualityScoreCategory, Integer> typedCategoryScores = new EnumMap<>(QualityScoreCategory.class);
        for (final Map.Entry<?, ?> entry : payload.entrySet()) {
            final QualityScoreCategory category = QualityScoreCategory.fromValue(stringValue(entry.getKey()));
            typedCategoryScores.put(category, intValue(entry.getValue()));
        }
        return Map.copyOf(new LinkedHashMap<>(typedCategoryScores));
    }

    public static Map<String, Object> toLegacyRepairStatus(final PlanningRepairOutcome outcome) {
        if (outcome == null) {
            return Map.of();
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", outcome.status().value());
        payload.put("repairAttempts", outcome.repairAttempts());
        payload.put("maxRepairAttempts", outcome.maxRepairAttempts());
        payload.put("actions", outcome.actions().stream().map(RepairAction::value).toList());
        payload.put("finalVerdict", outcome.finalVerdict().value());
        payload.put("score", outcome.score());
        payload.put("passingScore", outcome.passingScore());
        payload.put("findings", outcome.findings());
        if (!outcome.reason().isBlank()) {
            payload.put("reason", outcome.reason());
        }
        return Map.copyOf(payload);
    }

    public static PlanningRepairOutcome fromLegacyRepairStatus(final Map<String, Object> payload) {
        final Map<String, Object> normalizedPayload = payload == null ? Map.of() : payload;
        return new PlanningRepairOutcome(
                RepairStatus.fromValue(stringValue(normalizedPayload.get("status"))),
                intValue(normalizedPayload.get("repairAttempts")),
                intValue(normalizedPayload.get("maxRepairAttempts")),
                repairActionsFromPayload(normalizedPayload.get("actions")),
                QualityVerdict.fromValue(stringValue(normalizedPayload.get("finalVerdict"))),
                intValue(normalizedPayload.get("score")),
                intValue(normalizedPayload.get("passingScore")),
                stringList(normalizedPayload.get("findings")),
                stringValue(normalizedPayload.get("reason")));
    }

    private static Map<String, Object> toLegacyShapeAssessment(final PlanningShapeAssessment shapeAssessment) {
        if (shapeAssessment == null) {
            return Map.of();
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expectedShape", toLegacyShapeEnvelope(shapeAssessment.expectedShape()));
        payload.put("actualShape", toLegacyActualShape(shapeAssessment.actualShape()));
        payload.put("workstreamCountSatisfied", shapeAssessment.workstreamCountSatisfied());
        payload.put("phaseCountSatisfied", shapeAssessment.phaseCountSatisfied());
        payload.put("agentCountSatisfied", shapeAssessment.agentCountSatisfied());
        payload.put("missingCoverageFamilies", shapeAssessment.missingCoverageFamilies().stream().map(PlanningCoverageFamily::value).toList());
        return Map.copyOf(payload);
    }

    private static PlanningShapeAssessment fromLegacyShapeAssessment(final Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return PlanningShapeAssessment.unknown();
        }

        return new PlanningShapeAssessment(
                fromLegacyShapeEnvelope(payload.get("expectedShape")),
                fromLegacyActualShape(payload.get("actualShape")),
                booleanValue(payload.get("workstreamCountSatisfied")),
                booleanValue(payload.get("phaseCountSatisfied")),
                booleanValue(payload.get("agentCountSatisfied")),
                coverageFamiliesFromPayload(payload.get("missingCoverageFamilies")));
    }

    private static Map<String, Object> toLegacyShapeEnvelope(final PlanningShapeEnvelope envelope) {
        if (envelope == null) {
            return Map.of();
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("minWorkstreamCount", envelope.minWorkstreamCount());
        payload.put("maxWorkstreamCount", envelope.maxWorkstreamCount());
        payload.put("minPhaseCount", envelope.minPhaseCount());
        payload.put("maxPhaseCount", envelope.maxPhaseCount());
        payload.put("minAgentCount", envelope.minAgentCount());
        payload.put("maxAgentCount", envelope.maxAgentCount());
        payload.put("requiredCoverageFamilies", envelope.requiredCoverageFamilies().stream().map(PlanningCoverageFamily::value).toList());
        payload.put("outputBand", envelope.outputBand());
        payload.put("strongerModelWarningReason", envelope.strongerModelWarningReason());
        return Map.copyOf(payload);
    }

    private static PlanningShapeEnvelope fromLegacyShapeEnvelope(final Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return PlanningShapeEnvelope.unknown();
        }

        return new PlanningShapeEnvelope(
                intValue(payload.get("minWorkstreamCount")),
                intValue(payload.get("maxWorkstreamCount")),
                intValue(payload.get("minPhaseCount")),
                intValue(payload.get("maxPhaseCount")),
                intValue(payload.get("minAgentCount")),
                intValue(payload.get("maxAgentCount")),
                coverageFamiliesFromPayload(payload.get("requiredCoverageFamilies")),
                stringValue(payload.get("outputBand")),
                stringValue(payload.get("strongerModelWarningReason")));
    }

    private static Map<String, Object> toLegacyActualShape(final PlanningActualShape actualShape) {
        if (actualShape == null) {
            return Map.of();
        }

        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workstreamCount", actualShape.workstreamCount());
        payload.put("phaseCount", actualShape.phaseCount());
        payload.put("agentCount", actualShape.agentCount());
        payload.put("coveredFamilies", actualShape.coveredFamilies().stream().map(PlanningCoverageFamily::value).toList());
        payload.put("workstreamsWithoutPhases", actualShape.workstreamsWithoutPhases());
        return Map.copyOf(payload);
    }

    private static PlanningActualShape fromLegacyActualShape(final Object value) {
        if (!(value instanceof Map<?, ?> payload)) {
            return PlanningActualShape.empty();
        }

        return new PlanningActualShape(
                intValue(payload.get("workstreamCount")),
                intValue(payload.get("phaseCount")),
                intValue(payload.get("agentCount")),
                coverageFamiliesFromPayload(payload.get("coveredFamilies")),
                stringList(payload.get("workstreamsWithoutPhases")));
    }

    private static List<Map<String, Object>> toLegacyDeterministicFindings(final List<PlanningQualityFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        final List<Map<String, Object>> payload = new ArrayList<>();
        for (final PlanningQualityFinding finding : findings) {
            final Map<String, Object> findingPayload = new LinkedHashMap<>();
            findingPayload.put("code", finding.code().value());
            findingPayload.put("type", finding.type().value());
            findingPayload.put("message", finding.message());
            payload.add(Map.copyOf(findingPayload));
        }
        return List.copyOf(payload);
    }

    private static List<PlanningQualityFinding> fromLegacyDeterministicFindings(final Object value) {
        if (!(value instanceof List<?> payload)) {
            return List.of();
        }

        final List<PlanningQualityFinding> findings = new ArrayList<>();
        for (final Object entry : payload) {
            if (!(entry instanceof Map<?, ?> findingPayload)) {
                continue;
            }
            findings.add(new PlanningQualityFinding(
                    PlanningQualityFindingCode.fromValue(stringValue(findingPayload.get("code"))),
                    PlanningQualityFindingType.fromValue(stringValue(findingPayload.get("type"))),
                    stringValue(findingPayload.get("message"))));
        }
        return List.copyOf(findings);
    }

    private static List<RepairAction> repairActionsFromPayload(final Object actions) {
        if (!(actions instanceof List<?> actionValues)) {
            return List.of();
        }

        final List<RepairAction> repairActions = new ArrayList<>();
        for (final Object actionValue : actionValues) {
            repairActions.add(RepairAction.fromValue(stringValue(actionValue)));
        }
        return List.copyOf(repairActions);
    }

    private static List<PlanningCoverageFamily> coverageFamiliesFromPayload(final Object value) {
        if (!(value instanceof List<?> payload)) {
            return List.of();
        }

        final List<PlanningCoverageFamily> families = new ArrayList<>();
        for (final Object entry : payload) {
            families.add(PlanningCoverageFamily.fromValue(stringValue(entry)));
        }
        return List.copyOf(families);
    }

    private static ModelCapability modelCapabilityFromValue(final Object value) {
        final String normalizedValue = stringValue(value).toUpperCase();
        return switch (normalizedValue) {
            case "LOW" -> ModelCapability.LOW;
            case "MODERATE" -> ModelCapability.MODERATE;
            case "HIGH" -> ModelCapability.HIGH;
            case "UNKNOWN", "" -> ModelCapability.UNKNOWN;
            default -> ModelCapability.UNKNOWN;
        };
    }

    private static String stringValue(final Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean booleanValue(final Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private static int intValue(final Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (final NumberFormatException exception) {
            return 0;
        }
    }

    private static List<String> stringList(final Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        final List<String> normalizedValues = new ArrayList<>();
        for (final Object entry : listValue) {
            final String normalizedEntry = stringValue(entry);
            if (!normalizedEntry.isBlank()) {
                normalizedValues.add(normalizedEntry);
            }
        }
        return List.copyOf(normalizedValues);
    }
}
