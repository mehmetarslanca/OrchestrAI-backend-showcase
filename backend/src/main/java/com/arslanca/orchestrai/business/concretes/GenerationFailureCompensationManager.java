package com.arslanca.orchestrai.business.concretes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.abstracts.GenerationFailureCompensationService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class GenerationFailureCompensationManager implements GenerationFailureCompensationService {

  private static final Logger log = LoggerFactory.getLogger(GenerationFailureCompensationManager.class);
  private final ProjectRepository projectRepository;
  private final CreditManagementService creditManagementService;
  private final CreditTransactionService creditTransactionService;
  private final MeterRegistry meterRegistry;

  public GenerationFailureCompensationManager(
      final ProjectRepository projectRepository,
      final CreditManagementService creditManagementService,
      final CreditTransactionService creditTransactionService,
      final MeterRegistry meterRegistry) {
    this.projectRepository = projectRepository;
    this.creditManagementService = creditManagementService;
    this.creditTransactionService = creditTransactionService;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void compensateFailure(
      final UUID projectId,
      final Throwable error,
      final boolean requiresRefund,
      final GenerationFailureReason recoveryReason) {
    log.warn("Compensating failure for projectId={}, reason={}, requiresRefund={}", projectId, recoveryReason,
        requiresRefund, error);

    final var managedProjectOptional = projectRepository.findByIdForUpdate(projectId);
    if (managedProjectOptional.isEmpty()) {
      log.warn("Project not found for compensation: {}", projectId);
      return;
    }

    final var managedProject = managedProjectOptional.get();

    if (managedProject.getStatus() == ProjectStatus.FAILED) {
      log.info("Project is already marked as FAILED. Skipping compensation state change. projectId={}", projectId);
    } else if (managedProject.getStatus() == ProjectStatus.COMPLETED) {
      log.error("Attempted to compensate a COMPLETED project. Aborting. projectId={}", projectId);
      return;
    } else {
      final Map<String, Object> failureMetadata = new HashMap<>();
      failureMetadata.put("error", String.valueOf(error != null ? error.getMessage() : "Unknown error"));
      if (error != null) {
        failureMetadata.put("errorType", error.getClass().getSimpleName());
      }
      if (recoveryReason != null) {
        failureMetadata.put(ProjectStateService.RECOVERY_REASON_KEY, recoveryReason.metadataValue());
      }
      if (!hasMetadataKey(managedProject.getMetadata(), ProjectStateService.TERMINAL_STATE_SOURCE_KEY)) {
        failureMetadata.put(ProjectStateService.TERMINAL_STATE_SOURCE_KEY,
            ProjectStateService.TERMINAL_STATE_SOURCE_COMPENSATION);
      }
      failureMetadata.put(ProjectStateService.TERMINAL_STATE_AT_KEY, OffsetDateTime.now().toString());

      managedProject.markAsFailed(failureMetadata);
      projectRepository.save(managedProject);
      log.info("Project marked as FAILED in compensation. projectId={}", projectId);
    }

    final String creditOutcome = requiresRefund
        ? reverseCreditReservation(managedProject.getUser().getId(), managedProject.getProjectName(),
            managedProject.getMetadata(), projectId, recoveryReason)
        : captureReservedCredit(managedProject.getUser().getId(), managedProject.getMetadata(), projectId);

    if (creditOutcome != null && !creditOutcome.isBlank()) {
      managedProject.appendMetadata(Map.of(ProjectStateService.CREDIT_OUTCOME_KEY, creditOutcome));
      projectRepository.save(managedProject);
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void compensatePreProjectFailure(final UUID userId, final String projectName, final Throwable error) {
    log.warn("Compensating pre-project failure for userId={}, projectName={}", userId, projectName, error);
    creditManagementService.refundCredit(userId);
    creditTransactionService.recordRefund(userId, projectName);
    recordCompensationMetric("refunded");
    log.info("Pre-project compensation refund successful for userId={}", userId);
  }

  private String reverseCreditReservation(
      final UUID userId,
      final String projectName,
      final Map<String, Object> metadata,
      final UUID projectId,
      final GenerationFailureReason recoveryReason) {

    final String reservationReference = extractReservationReference(metadata);
    final TransactionStatus usageStatus = reservationReference == null
        ? null
        : creditTransactionService.getProjectGenerationReservationStatus(reservationReference).orElse(null);

    if (usageStatus == TransactionStatus.PENDING) {
      creditManagementService.releaseReservedCredit(userId);
      creditTransactionService.failProjectGenerationReservation(reservationReference, reasonValue(recoveryReason));
      recordCompensationMetric("refunded");
      log.info("Reserved credit released during compensation. projectId={}, userId={}", projectId, userId);
      return ProjectStateService.CREDIT_OUTCOME_RELEASED;
    }

    if (usageStatus == TransactionStatus.FAILED) {
      log.info("Reservation is already marked FAILED, skipping compensation. projectId={}, userId={}", projectId,
          userId);
      return null;
    }

    if (usageStatus == TransactionStatus.COMPLETED) {
      if (!creditTransactionService.hasRefunded(projectId.toString())) {
        creditManagementService.refundCredit(userId);
        creditTransactionService.recordRefund(userId, projectName, projectId.toString());
        recordCompensationMetric("refunded");
        log.info("Captured credit refunded during compensation. projectId={}, userId={}", projectId, userId);
        return ProjectStateService.CREDIT_OUTCOME_REFUNDED;
      }
      return null;
    }

    if (!creditTransactionService.hasRefunded(projectId.toString())) {
      creditManagementService.refundCredit(userId);
      creditTransactionService.recordRefund(userId, projectName, projectId.toString());
      recordCompensationMetric("refunded");
      log.info("Legacy refund fallback executed during compensation. projectId={}, userId={}", projectId, userId);
      return ProjectStateService.CREDIT_OUTCOME_REFUNDED;
    }

    log.info("Refund already processed for projectId={}, skipping.", projectId);
    return null;
  }

  private String captureReservedCredit(
      final UUID userId,
      final Map<String, Object> metadata,
      final UUID projectId) {
    final String reservationRef = extractReservationReference(metadata);
    if (reservationRef == null) {
      return null;
    }

    final TransactionStatus usageStatus = creditTransactionService.getProjectGenerationReservationStatus(reservationRef)
        .orElse(null);
    if (usageStatus == TransactionStatus.PENDING) {
      creditManagementService.captureReservedCredit(userId);
      creditTransactionService.captureProjectGenerationReservation(reservationRef);
      recordCompensationMetric("captured");
      log.info("Reserved credit explicitly captured for non-refunded failure. projectId={}", projectId);
      return ProjectStateService.CREDIT_OUTCOME_CAPTURED;
    }

    if (usageStatus == TransactionStatus.COMPLETED) {
      return null;
    }

    if (usageStatus == TransactionStatus.FAILED) {
      return null;
    }

    return null;
  }

  private String extractReservationReference(final Map<String, Object> metadata) {
    if (metadata == null) {
      return null;
    }
    final Object value = metadata.get(ProjectStateService.CREDIT_RESERVATION_REFERENCE_KEY);
    return value == null ? null : String.valueOf(value);
  }

  private boolean hasMetadataKey(final Map<String, Object> metadata, final String key) {
    return metadata != null && metadata.containsKey(key);
  }

  private String reasonValue(final GenerationFailureReason recoveryReason) {
    return recoveryReason == null ? null : recoveryReason.metadataValue();
  }

  private void recordCompensationMetric(final String outcome) {
    meterRegistry.counter("project_generation.credit.compensation", "outcome", outcome).increment();
  }
}
