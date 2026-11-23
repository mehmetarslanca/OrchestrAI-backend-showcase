package com.arslanca.orchestrai.business.dtos.orchestration;

public record PlanningQualityFinding(
        PlanningQualityFindingCode code,
        PlanningQualityFindingType type,
        String message) {

    public PlanningQualityFinding {
        code = code == null ? PlanningQualityFindingCode.UNKNOWN : code;
        type = type == null ? PlanningQualityFindingType.UNKNOWN : type;
        message = message == null ? "" : message.trim();
    }
}
