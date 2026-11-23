package com.arslanca.orchestrai.business.planning.preparation;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;

public record ChatPlanningEvaluationResult(
        ChatPlanningReadinessStatus status,
        ChatPlanningSnapshot snapshot,
        long messageCount,
        String reason) {

    public boolean ready() {
        return status == ChatPlanningReadinessStatus.READY_FOR_PREPARATION;
    }

    public boolean failed() {
        return status == ChatPlanningReadinessStatus.ANALYSIS_FAILED;
    }
}
