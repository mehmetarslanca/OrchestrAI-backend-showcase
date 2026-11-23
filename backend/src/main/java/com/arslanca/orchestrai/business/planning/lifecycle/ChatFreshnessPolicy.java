package com.arslanca.orchestrai.business.planning.lifecycle;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class ChatFreshnessPolicy {

  private final MessageRepository messageRepository;
  private final ProjectGenerationPreparationRepository preparationRepository;
  private final ProjectPlanningPreviewRepository planningPreviewRepository;

  public ChatFreshnessPolicy(
      final MessageRepository messageRepository,
      final ProjectGenerationPreparationRepository preparationRepository,
      final ProjectPlanningPreviewRepository planningPreviewRepository) {
    this.messageRepository = messageRepository;
    this.preparationRepository = preparationRepository;
    this.planningPreviewRepository = planningPreviewRepository;
  }

  public void ensurePreparationCurrent(final ProjectGenerationPreparationEntity preparation) {
    if (preparation.getChatId() == null || preparation.getChatMessageCount() == null) {
      return;
    }

    final long currentMessageCount = messageRepository.countByChatId(preparation.getChatId());
    if (currentMessageCount == preparation.getChatMessageCount()) {
      return;
    }

    preparation.markAsInvalidated();
    preparationRepository.save(preparation);
    invalidateActivePreviews(preparation.getId());

    throw new LifecycleViolationException(
        LifecycleViolationReason.CHAT_CHANGED,
        "The chat changed after this review was prepared. Please prepare generation again.");
  }

  private void invalidateActivePreviews(final UUID preparationId) {
    final List<ProjectPlanningPreviewEntity> previewsToInvalidate = planningPreviewRepository
        .findByPreparationIdOrderByCreatedAtDesc(preparationId)
        .stream()
        .filter(this::isActivePreview)
        .toList();

    previewsToInvalidate.forEach(preview -> preview.markInvalidated("chat_changed"));
    if (!previewsToInvalidate.isEmpty()) {
      planningPreviewRepository.saveAll(previewsToInvalidate);
    }
  }

  private boolean isActivePreview(final ProjectPlanningPreviewEntity preview) {
    return switch (preview.getPreviewStatus()) {
      case DRAFT, READY_FOR_REVIEW, ACCEPTED -> true;
      default -> false;
    };
  }
}
