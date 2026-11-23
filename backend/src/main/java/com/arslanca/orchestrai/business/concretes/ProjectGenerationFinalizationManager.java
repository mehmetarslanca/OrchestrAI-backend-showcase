package com.arslanca.orchestrai.business.concretes;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationFinalizationService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectGenerationFinalizationManager implements ProjectGenerationFinalizationService {

  private final ProjectStateService projectStateService;
  private final CreditManagementService creditManagementService;
  private final CreditTransactionService creditTransactionService;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finalizeSuccess(
      final UUID projectId,
      final UUID userId,
      final String creditReservationReference,
      final DeliveryResult result,
      final ResolvedHeavyModel resolvedHeavyModel,
      final com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection starterSelection,
      final com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter materializedStarter,
      final PlanningQualitySummary planningQualitySummary,
      final PlanningRepairOutcome planningRepairOutcome) {
    projectStateService.updateProjectAsCompleted(
        projectId,
        result,
        resolvedHeavyModel,
        starterSelection,
        materializedStarter,
        planningQualitySummary,
        planningRepairOutcome);
    creditManagementService.captureReservedCredit(userId);
    creditTransactionService.captureProjectGenerationReservation(creditReservationReference);
  }
}
