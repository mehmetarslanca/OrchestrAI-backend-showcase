package com.arslanca.orchestrai.core.exception.type;

import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

import java.util.UUID;

public class ProjectGenerationCancelledException extends RuntimeException {

    private final UUID projectId;
    private final GenerationFailureReason cancellationReason;

    public ProjectGenerationCancelledException(final UUID projectId, final GenerationFailureReason cancellationReason) {
        super("Project generation cancelled: projectId=" + projectId + ", reason="
                + (cancellationReason == null ? "UNKNOWN" : cancellationReason.metadataValue()));
        this.projectId = projectId;
        this.cancellationReason = cancellationReason;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public GenerationFailureReason getFailureReason() {
        return cancellationReason;
    }

    public String getCancellationReason() {
        return cancellationReason == null ? "UNKNOWN" : cancellationReason.metadataValue();
    }
}
