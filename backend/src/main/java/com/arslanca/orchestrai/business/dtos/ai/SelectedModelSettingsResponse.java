package com.arslanca.orchestrai.business.dtos.ai;

import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;

import java.util.UUID;

public record SelectedModelSettingsResponse(
        String modelKey,
        CredentialSource authSource,
        UUID selectedConnectionId,
        AiConnectionExecutionClass executionClass,
        boolean available) {
}
