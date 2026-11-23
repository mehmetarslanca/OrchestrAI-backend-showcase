package com.arslanca.orchestrai.business.abstracts;

import java.util.Optional;
import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

public interface PromptAnalyzerService {

    default StructuredAnalysis analyzePrompt(final String userPrompt) {
        return analyzePrompt(null, userPrompt);
    }

    StructuredAnalysis analyzePrompt(UUID userId, String userPrompt);

    default StructuredAnalysis analyzePlanningConversation(final String planningConversationSummary) {
        return analyzePlanningConversation(null, planningConversationSummary);
    }

    StructuredAnalysis analyzePlanningConversation(UUID userId, String planningConversationSummary);

    default ChatPlanningSnapshot refreshChatPlanningSnapshot(
            final Optional<ChatPlanningSnapshot> previousSnapshot,
            final String recentMessages) {
        return refreshChatPlanningSnapshot(null, previousSnapshot, recentMessages);
    }

    ChatPlanningSnapshot refreshChatPlanningSnapshot(
            UUID userId,
            Optional<ChatPlanningSnapshot> previousSnapshot,
            String recentMessages);
}
