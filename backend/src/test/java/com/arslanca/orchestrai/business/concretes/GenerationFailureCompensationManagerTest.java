package com.arslanca.orchestrai.business.concretes;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class GenerationFailureCompensationManagerTest {

  @Mock
  private ProjectRepository projectRepository;

  @Mock
  private CreditManagementService creditManagementService;

  @Mock
  private CreditTransactionService creditTransactionService;

  private GenerationFailureCompensationManager compensationManager;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    compensationManager = new GenerationFailureCompensationManager(
        projectRepository,
        creditManagementService,
        creditTransactionService,
        meterRegistry);
  }

  @Test
  @DisplayName("compensateFailure: explicit cancel after processing should capture reserved credit and persist metadata")
  void shouldCaptureCreditWhenRequiresRefundIsFalse() {
    final UUID projectId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final String reservationRef = "res-123";

    final ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
    final UserEntity user = org.mockito.Mockito.mock(UserEntity.class);

    when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
    when(project.getStatus()).thenReturn(ProjectStatus.PROCESSING);
    when(project.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(userId);

    final Map<String, Object> metadata = new HashMap<>();
    metadata.put(ProjectStateService.CREDIT_RESERVATION_REFERENCE_KEY, reservationRef);
    when(project.getMetadata()).thenReturn(metadata);
    when(creditTransactionService.getProjectGenerationReservationStatus(reservationRef))
        .thenReturn(Optional.of(TransactionStatus.PENDING));

    compensationManager.compensateFailure(
        projectId,
        new RuntimeException("Test error"),
        false,
        GenerationFailureReason.USER_CANCELLED);

    verify(project)
        .markAsFailed(argThat(failureMetadata -> GenerationFailureReason.USER_CANCELLED.metadataValue().equals(
            failureMetadata.get(ProjectStateService.RECOVERY_REASON_KEY))
            && failureMetadata.containsKey(ProjectStateService.TERMINAL_STATE_AT_KEY)));
    verify(project).appendMetadata(argThat(metadataUpdate -> ProjectStateService.CREDIT_OUTCOME_CAPTURED.equals(
        metadataUpdate.get(ProjectStateService.CREDIT_OUTCOME_KEY))));
    verify(projectRepository, atLeastOnce()).save(project);

    verify(creditManagementService).captureReservedCredit(userId);
    verify(creditTransactionService).captureProjectGenerationReservation(reservationRef);
    verify(creditManagementService, never()).releaseReservedCredit(userId);
    verify(creditManagementService, never()).refundCredit(userId);
  }

  @Test
  @DisplayName("compensateFailure: system failure before completion should release reserved credit and persist metadata")
  void shouldReleaseCreditWhenRequiresRefundIsTrue() {
    final UUID projectId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final String reservationRef = "res-123";
    final String projectName = "my-project";

    final ProjectEntity project = org.mockito.Mockito.mock(ProjectEntity.class);
    final UserEntity user = org.mockito.Mockito.mock(UserEntity.class);

    when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
    when(project.getStatus()).thenReturn(ProjectStatus.PENDING);
    when(project.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(userId);
    when(project.getProjectName()).thenReturn(projectName);

    final Map<String, Object> metadata = new HashMap<>();
    metadata.put(ProjectStateService.CREDIT_RESERVATION_REFERENCE_KEY, reservationRef);
    when(project.getMetadata()).thenReturn(metadata);
    when(creditTransactionService.getProjectGenerationReservationStatus(reservationRef))
        .thenReturn(Optional.of(TransactionStatus.PENDING));

    compensationManager.compensateFailure(
        projectId,
        new RuntimeException("Internal error"),
        true,
        GenerationFailureReason.PIPELINE_FAILURE);

    verify(project)
        .markAsFailed(argThat(failureMetadata -> GenerationFailureReason.PIPELINE_FAILURE.metadataValue().equals(
            failureMetadata.get(ProjectStateService.RECOVERY_REASON_KEY))));
    verify(project).appendMetadata(argThat(metadataUpdate -> ProjectStateService.CREDIT_OUTCOME_RELEASED.equals(
        metadataUpdate.get(ProjectStateService.CREDIT_OUTCOME_KEY))));
    verify(creditManagementService).releaseReservedCredit(userId);
    verify(creditTransactionService).failProjectGenerationReservation(
        reservationRef,
        GenerationFailureReason.PIPELINE_FAILURE.metadataValue());
    verify(creditManagementService, never()).captureReservedCredit(userId);
  }
}
