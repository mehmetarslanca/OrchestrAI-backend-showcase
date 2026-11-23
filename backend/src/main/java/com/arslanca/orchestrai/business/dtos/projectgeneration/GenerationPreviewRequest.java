package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerationPreviewRequest(
        @NotNull(message = "preparationId is required")
        UUID preparationId,

        @Size(max = 120, message = "starterKey cannot exceed 120 characters")
        String starterKey) {
}
