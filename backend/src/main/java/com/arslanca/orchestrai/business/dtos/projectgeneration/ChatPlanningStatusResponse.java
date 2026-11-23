package com.arslanca.orchestrai.business.dtos.projectgeneration;

import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;

public record ChatPlanningStatusResponse(
        boolean ready,
        ChatPlanningReadinessStatus status,
        String reason,
        GenerationPreparationResponse preparation
) {

    public static ChatPlanningStatusResponse notReady(
            final ChatPlanningReadinessStatus status,
            final String reason,
            final GenerationPreparationResponse preparation) {
        return new ChatPlanningStatusResponse(false, status, reason, preparation);
    }

    public static ChatPlanningStatusResponse analyzing(final String reason) {
        return new ChatPlanningStatusResponse(false, ChatPlanningReadinessStatus.ANALYZING, reason, null);
    }

    public static ChatPlanningStatusResponse ready(
            final String reason,
            final GenerationPreparationResponse preparation) {
        return new ChatPlanningStatusResponse(true, ChatPlanningReadinessStatus.READY_FOR_PREPARATION, reason, preparation);
    }
}
