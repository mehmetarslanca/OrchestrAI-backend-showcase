package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

public interface GenerationFailureCompensationService {

    /**
     * Compensates a generation failure by marking the project as FAILED,
     * and optionally refunding the credit and recording a transaction.
     * Guarantees exact-once refund execution.
     */
    void compensateFailure(UUID projectId, Throwable error, boolean requiresRefund, GenerationFailureReason recoveryReason);
    
    /**
     * Compensates a specific user and project name, typically when project ID is unknown or before project creation.
     */
    void compensatePreProjectFailure(UUID userId, String projectName, Throwable error);
}
