package com.arslanca.orchestrai.business.dtos.conversation;

import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;

/**
 * Chat SSE stream event'i.
 *
 * <p>
 * ChatGPT-tarzi token-by-token streaming event'lerini tasir.
 * </p>
 *
 * <p>Chat event tipleri:</p>
 * <ul>
 *   <li>{@code TOKEN} — LLM'den gelen bir metin parçası</li>
 *   <li>{@code DONE} — Chat stream tamamlandı</li>
 *   <li>{@code ERROR} — Stream sırasında hata oluştu</li>
 * </ul>
 *
 */
public record ChatSseEvent(
        String type,
        String content,
        String stage,
        ChatPlanningStatusResponse planning
) {

    // ---- Chat events ----

    public static ChatSseEvent token(final String tokenContent) {
        return new ChatSseEvent("TOKEN", tokenContent, null, null);
    }

    public static ChatSseEvent done(final ChatPlanningStatusResponse planning) {
        return new ChatSseEvent("DONE", "", null, planning);
    }

    public static ChatSseEvent error(final String errorMessage) {
        return new ChatSseEvent("ERROR", errorMessage, "FAILED", null);
    }
}
