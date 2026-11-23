package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public interface AiUsageCaptureService {

    void capture(
            UUID userId,
            String requestId,
            AiUsageSurface surface,
            AiUsageEventStatus status,
            ResolvedAiExecutionRoute route,
            Prompt prompt,
            String responseText,
            ChatResponse response,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Map<String, Object> metadata);
}
