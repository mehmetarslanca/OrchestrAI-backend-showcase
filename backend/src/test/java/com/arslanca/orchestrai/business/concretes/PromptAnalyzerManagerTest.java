package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.enums.ChatPlanningHandoffState;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.PromptRejectionException;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PromptAnalyzerManagerTest {

    @Mock
    private ChatModel lightChatModel;

    @Mock
    private AiExecutionRouteResolverService aiExecutionRouteResolverService;

    @Mock
    private AiUsageCaptureService aiUsageCaptureService;

    @Mock
    private ObjectMapper objectMapper;

    private PromptAnalyzerManager promptAnalyzerManager;

    @BeforeEach
    void setUp() {
        when(aiExecutionRouteResolverService.resolve(any(), any(), any())).thenReturn(
                new ResolvedAiExecutionRoute(AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE, lightChatModel, null, null));
        promptAnalyzerManager = new PromptAnalyzerManager(aiExecutionRouteResolverService, aiUsageCaptureService, objectMapper);
    }

    private void mockChatResponse(final String jsonResponse) {
        final Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(mock(org.springframework.ai.chat.messages.AssistantMessage.class));
        when(generation.getOutput().getText()).thenReturn(jsonResponse);

        final ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);

        when(lightChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Nested
    class AnalyzePromptTests {

        @Test
        void shouldReturnStructuredAnalysisWhenResponseIsSafeAndValid() throws Exception {
            final String rawJson = "{\"is_safe\": true, \"project_name\": \"TestProject\"}";
            mockChatResponse(rawJson);

            final StructuredAnalysis analysis = new StructuredAnalysis(
                    "TestProject", "App", List.of("UI", "API"), List.of("feat1"),
                    "LOW", false, null, "Summary");

            when(objectMapper.readValue(rawJson, StructuredAnalysis.class)).thenReturn(analysis);

            final StructuredAnalysis result = promptAnalyzerManager.analyzePrompt("create a test project");

            assertThat(result).isNotNull();
            assertThat(result.projectName()).isEqualTo("TestProject");
            verify(lightChatModel).call(any(Prompt.class));
            verify(aiUsageCaptureService).capture(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(objectMapper).readValue(rawJson, StructuredAnalysis.class);
        }

        @Test
        void shouldReturnStructuredAnalysisForPlanningConversation() throws Exception {
            final String rawJson = "{\"is_safe\": true, \"project_name\": \"PlanningProject\"}";
            mockChatResponse(rawJson);

            final StructuredAnalysis analysis = new StructuredAnalysis(
                    "PlanningProject", "API", List.of("go", "postgres"), List.of("events"),
                    "MODERATE", false, null, "Planning Summary");

            when(objectMapper.readValue(rawJson, StructuredAnalysis.class)).thenReturn(analysis);

            final StructuredAnalysis result = promptAnalyzerManager.analyzePlanningConversation("planning summary");

            assertThat(result.projectName()).isEqualTo("PlanningProject");
        }

        @Test
        void shouldThrowPromptRejectionExceptionWhenResponseIsUnsafe() throws Exception {
            final String rawJson = "{\"is_safe\": false, \"malicious_reason\": \"Injection attempt\"}";
            mockChatResponse(rawJson);

            final StructuredAnalysis analysis = new StructuredAnalysis(
                    null, null, null, null,
                    null, true, "Injection attempt", null);

            when(objectMapper.readValue(rawJson, StructuredAnalysis.class)).thenReturn(analysis);

            assertThatThrownBy(() -> promptAnalyzerManager.analyzePrompt("bad prompt"))
                    .isInstanceOf(PromptRejectionException.class)
                    .hasMessage("Your request could not be processed.");

            verify(aiUsageCaptureService).capture(any(), any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED),
                    any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldThrowLlmResponseParseExceptionWhenJsonIsInvalid() throws Exception {
            final String rawJson = "{\"invalid\": \"json\"}";
            mockChatResponse(rawJson);

            when(objectMapper.readValue(rawJson, StructuredAnalysis.class))
                    .thenThrow(new RuntimeException("JSON parse error"));

            assertThatThrownBy(() -> promptAnalyzerManager.analyzePrompt("some prompt"))
                    .isInstanceOf(LlmResponseParseException.class)
                    .hasMessageContaining("Failed to parse LLM analysis output");

            verify(aiUsageCaptureService).capture(any(), any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED),
                    any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class RefreshChatPlanningSnapshotTests {

        @Test
        void shouldReturnSnapshotWhenResponseIsValid() throws Exception {
            final String rawJson = "{\"handoffState\": \"CONTINUE_DISCOVERY\"}";
            mockChatResponse(rawJson);

            final ChatPlanningSnapshot snapshot = new ChatPlanningSnapshot(
                    "TestProject",
                    "API",
                    "Analytics API",
                    "Go, Postgres",
                    "B2B teams",
                    List.of("analytics backend"),
                    List.of("go", "postgres"),
                    List.of("none"),
                    List.of(),
                    List.of("multi-tenant"),
                    ChatPlanningHandoffState.CONTINUE_DISCOVERY,
                    "Need one more clarification.",
                    null);

            when(objectMapper.readValue(rawJson, ChatPlanningSnapshot.class)).thenReturn(snapshot);

            final ChatPlanningSnapshot result = promptAnalyzerManager.refreshChatPlanningSnapshot(Optional.empty(), "some msg");

            assertThat(result).isNotNull();
            assertThat(result.projectName()).isEqualTo("TestProject");
        }

        @Test
        void shouldThrowLlmResponseParseExceptionWhenSnapshotJsonIsInvalid() throws Exception {
            final String rawJson = "{\"handoffState\": \"CONTINUE_DISCOVERY\"}";
            mockChatResponse(rawJson);

            when(objectMapper.readValue(rawJson, ChatPlanningSnapshot.class))
                    .thenThrow(new RuntimeException("Snapshot parse error"));

            assertThatThrownBy(() -> promptAnalyzerManager.refreshChatPlanningSnapshot(Optional.empty(), "some msg"))
                    .isInstanceOf(LlmResponseParseException.class)
                    .hasMessageContaining("Failed to parse chat planning snapshot output");

            verify(aiUsageCaptureService).capture(any(), any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED),
                    any(), any(), any(), any(), any(), any(), any());
        }
    }
}
