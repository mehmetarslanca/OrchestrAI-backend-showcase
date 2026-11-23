package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record QualityGateResult(
        QualityVerdict verdict,
        boolean passed,
        int totalScore,
        int passingScore,
        Map<String, Integer> categoryScores,
        List<String> findings,
        List<PlanningQualityFinding> deterministicFindings,
        PlanningShapeAssessment shapeAssessment) {

    public QualityGateResult {
        verdict = verdict == null ? QualityVerdict.REJECT : verdict;
        categoryScores = categoryScores == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(categoryScores));
        findings = findings == null ? List.of() : List.copyOf(findings);
        deterministicFindings = deterministicFindings == null ? List.of() : List.copyOf(deterministicFindings);
        shapeAssessment = shapeAssessment == null ? PlanningShapeAssessment.unknown() : shapeAssessment;
    }

    public QualityGateResult(
            final QualityVerdict verdict,
            final boolean passed,
            final int totalScore,
            final int passingScore,
            final Map<String, Integer> categoryScores,
            final List<String> findings) {
        this(verdict, passed, totalScore, passingScore, categoryScores, findings, List.of(), PlanningShapeAssessment.unknown());
    }
}
