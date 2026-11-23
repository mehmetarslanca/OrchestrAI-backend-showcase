package com.arslanca.orchestrai.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleLightChatModelTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private ToolCallingManager toolCallingManager;

    private OpenAiCompatibleLightChatModel model;

    @BeforeEach
    void setUp() {
        model = new OpenAiCompatibleLightChatModel(
                restClientBuilder,
                new ObjectMapper(),
                "http://localhost:4000",
                "test-key",
                "gemma4-e4b",
                0.2d,
                4096,
                toolCallingManager);
    }

    @Test
    void shouldAdaptUnknownToolNameToSingleAvailableTool() throws Exception {
        final Method resolveToolName = OpenAiCompatibleLightChatModel.class
                .getDeclaredMethod("resolveToolName", String.class, Set.class);
        resolveToolName.setAccessible(true);

        final String resolved = (String) resolveToolName.invoke(model, "complete_planning", Set.of("confirm_plan"));

        assertThat(resolved).isEqualTo("confirm_plan");
    }

    @Test
    void shouldParseInlineToolCallsFromAssistantJsonPayload() throws Exception {
        final Method parseInlineToolCalls = OpenAiCompatibleLightChatModel.class
                .getDeclaredMethod("parseInlineToolCalls", String.class);
        parseInlineToolCalls.setAccessible(true);

        @SuppressWarnings("unchecked")
        final List<AssistantMessage.ToolCall> toolCalls = (List<AssistantMessage.ToolCall>) parseInlineToolCalls.invoke(
                model,
                "{\"tool_calls\": [{\"function\": \"confirm_plan\", \"args\": {\"completion\": \"Planlama tamamlandı\"}}]}");

        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.getFirst().name()).isEqualTo("confirm_plan");
        final JsonNode args = new ObjectMapper().readTree(toolCalls.getFirst().arguments());
        assertThat(args.path("completion").asText()).isEqualTo("Planlama tamamlandı");
    }

    @Test
    void shouldNormalizeWrappedAssistantContent() throws Exception {
        final Method normalizeAssistantText = OpenAiCompatibleLightChatModel.class
                .getDeclaredMethod("normalizeAssistantText", String.class);
        normalizeAssistantText.setAccessible(true);

        final String normalized = (String) normalizeAssistantText.invoke(
                model,
                "{\"name\":\"assistant\",\"content\":\"Merhaba dunya\"}");

        assertThat(normalized).isEqualTo("Merhaba dunya");
    }

    @Test
    void shouldBuildSyntheticCompletionAfterConfirmPlanToolCall() throws Exception {
        final Method buildToolDrivenCompletionResponse = OpenAiCompatibleLightChatModel.class
                .getDeclaredMethod("buildToolDrivenCompletionResponse", ChatResponse.class);
        buildToolDrivenCompletionResponse.setAccessible(true);

        final ChatResponse toolCallResponse = new ChatResponse(
                List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("id-1", "function", "confirm_plan", "{}")))
                        .build())),
                ChatResponseMetadata.builder().model("gemma4-e4b").build());

        final ChatResponse result = (ChatResponse) buildToolDrivenCompletionResponse.invoke(model, toolCallResponse);

        assertThat(result.getResult().getOutput().getText()).isEqualTo("Planlama tamamlandi. Blueprint generation review hazirlaniyor.");
        assertThat(result.hasToolCalls()).isFalse();
    }
}
