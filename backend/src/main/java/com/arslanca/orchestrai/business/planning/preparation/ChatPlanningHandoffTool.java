package com.arslanca.orchestrai.business.planning.preparation;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.enums.ChatPlanningHandoffState;

public class ChatPlanningHandoffTool {

    public static final String TOOL_NAME = "confirm_plan";

    private final UUID chatId;
    private final TransientChatPlanningHandoffRegistry handoffRegistry;

    public ChatPlanningHandoffTool(
            final UUID chatId,
            final TransientChatPlanningHandoffRegistry handoffRegistry) {
        this.chatId = chatId;
        this.handoffRegistry = handoffRegistry;
    }

    @Tool(name = TOOL_NAME, description = "Call this exactly once when planning is complete and the project is ready to move into blueprint generation review. Pass one concise Turkish sentence in `completion` explaining that the planning phase is complete and why blueprint generation review can begin.")
    public String completePlanningHandoff(
            @ToolParam(description = "One concise Turkish sentence explaining that planning is complete and blueprint generation review can begin.") final String completion) {
        final ChatPlanningSnapshot snapshot = new ChatPlanningSnapshot(
                "",
                "",
                completion,
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ChatPlanningHandoffState.READY_FOR_PREPARATION,
                completion,
                ChatPlanningSnapshot.CURRENT_CONTRACT_VERSION);
        handoffRegistry.record(chatId, snapshot);
        return "Planning handoff recorded. Continue with a short natural-language confirmation to the user.";
    }
}
