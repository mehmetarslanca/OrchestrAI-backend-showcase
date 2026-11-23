package com.arslanca.orchestrai.business.dtos.projectgeneration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerationPreparationRequest(
        @NotBlank(message = "Project name cannot be blank")
        @Size(max = 200, message = "Project name cannot exceed 200 characters")
        String projectName,

        @NotBlank(message = "ideTarget cannot be blank")
        @Pattern(regexp = "cursor|antigravity|windsurf|generic", message = "ideTarget must be one of: cursor, antigravity, windsurf, generic")
        String ideTarget,

        @NotBlank(message = "Prompt cannot be blank")
        @Size(max = 10000, message = "Prompt cannot exceed 10000 characters")
        String prompt,

        String sourceChannel) {

    public GenerationSourceChannel resolvedSourceChannel() {
        return GenerationSourceChannel.fromValue(sourceChannel);
    }
}
