package com.arslanca.orchestrai.business.dtos.projectgeneration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChatGenerationPreparationRequest(
        @NotBlank(message = "ideTarget cannot be blank")
        @Pattern(regexp = "cursor|antigravity|windsurf|generic", message = "ideTarget must be one of: cursor, antigravity, windsurf, generic")
        String ideTarget) {
}
