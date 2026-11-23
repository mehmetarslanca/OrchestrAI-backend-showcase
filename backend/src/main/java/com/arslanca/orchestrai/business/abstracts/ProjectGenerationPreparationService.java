package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatGenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;

public interface ProjectGenerationPreparationService {

    GenerationPreparationResponse prepareGeneration(UUID userId, GenerationPreparationRequest request);

    GenerationPreparationResponse prepareGenerationFromChat(UUID userId, UUID chatId, ChatGenerationPreparationRequest request);

    ChatPlanningStatusResponse getChatPlanningStatus(UUID userId, UUID chatId);

    ChatPlanningStatusResponse evaluateChatPlanningAfterAssistantTurn(UUID userId, UUID chatId, String ideTarget);

    ChatPlanningStatusResponse retryChatPlanningAfterAssistantTurn(UUID userId, UUID chatId, String ideTarget);

    void registerToolDrivenPlanningHandoff(UUID userId, UUID chatId, ChatPlanningSnapshot snapshot);
}
