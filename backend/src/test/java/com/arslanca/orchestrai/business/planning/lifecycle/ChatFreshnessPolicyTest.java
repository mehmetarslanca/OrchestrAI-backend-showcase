package com.arslanca.orchestrai.business.planning.lifecycle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

@ExtendWith(MockitoExtension.class)
class ChatFreshnessPolicyTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ProjectGenerationPreparationRepository preparationRepository;

    @Mock
    private ProjectPlanningPreviewRepository planningPreviewRepository;

    private ChatFreshnessPolicy chatFreshnessPolicy;
    private ProjectGenerationPreparationEntity preparation;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatFreshnessPolicy = new ChatFreshnessPolicy(
                messageRepository,
                preparationRepository,
                planningPreviewRepository);

        chatId = UUID.randomUUID();
        preparation = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                chatId,
                10L,
                GenerationSourceChannel.WEB.name(),
                "Acme Platform",
                "cursor",
                "Build an orchestration workspace",
                Map.of("projectName", "Acme Platform"),
                Map.of("recommendedStarter", Map.of("key", "nextjs-saas-shell")),
                "1.0",
                Map.of()
        );
    }

    @Test
    void shouldPassWhenChatIdIsNull() {
        final ProjectGenerationPreparationEntity prepWithoutChat = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                null,
                null,
                GenerationSourceChannel.WEB.name(),
                "Acme",
                "cursor",
                "summary",
                Map.of(),
                Map.of(),
                "1.0",
                Map.of()
        );

        chatFreshnessPolicy.ensurePreparationCurrent(prepWithoutChat);

        verify(messageRepository, never()).countByChatId(any());
        verify(preparationRepository, never()).save(any());
    }

    @Test
    void shouldPassWhenMessageCountMatches() {
        when(messageRepository.countByChatId(chatId)).thenReturn(10L);

        chatFreshnessPolicy.ensurePreparationCurrent(preparation);

        verify(messageRepository).countByChatId(chatId);
        verify(preparationRepository, never()).save(any());
    }

    @Test
    void shouldThrowAndInvalidateWhenMessageCountDiffers() {
        when(messageRepository.countByChatId(chatId)).thenReturn(11L);

        final ProjectPlanningPreviewEntity activePreview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "some-starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache",
                "1.0"
        );
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparation.getId()))
                .thenReturn(List.of(activePreview));

        assertThatThrownBy(() -> chatFreshnessPolicy.ensurePreparationCurrent(preparation))
                .isInstanceOf(LifecycleViolationException.class)
                .hasMessageContaining("chat changed")
                .extracting("reason")
                .isEqualTo(LifecycleViolationReason.CHAT_CHANGED);

        verify(preparationRepository).save(preparation);
        assert preparation.getStatus() == ProjectGenerationPreparationStatus.INVALIDATED;

        verify(planningPreviewRepository).saveAll(List.of(activePreview));
        assert activePreview.getPreviewStatus() == com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus.INVALIDATED;
    }
}
