package com.arslanca.orchestrai.business.concretes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.CreditTransactionService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import com.arslanca.orchestrai.entity.enums.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class GenerationFailureCompensationManagerConcurrencyTest {

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
                projectRepository, creditManagementService, creditTransactionService, meterRegistry);
    }

    @Test
    @DisplayName("Should be idempotent if called concurrently and status is already FAILED with FAILED transaction")
    void shouldBeIdempotentForAlreadyFailedProjectAndTransaction() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String reservationRef = "res-123";

        ProjectEntity project = mock(ProjectEntity.class);
        UserEntity user = mock(UserEntity.class);

        // Project is already marked FAILED by the first concurrent thread
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(project.getStatus()).thenReturn(ProjectStatus.FAILED);
        when(project.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("creditReservationReference", reservationRef);
        when(project.getMetadata()).thenReturn(metadata);

        // The transaction status is already FAILED
        when(creditTransactionService.getProjectGenerationReservationStatus(reservationRef))
                .thenReturn(Optional.of(TransactionStatus.FAILED));

        compensationManager.compensateFailure(projectId, new RuntimeException("Test error"), true,
                GenerationFailureReason.PIPELINE_FAILURE);

        // Assert no project save occurred again
        verify(project, never()).markAsFailed(any());
        verify(projectRepository, never()).save(any());

        // Assert no credit services were invoked again
        verify(creditManagementService, never()).releaseReservedCredit(any());
        verify(creditManagementService, never()).refundCredit(any());
        verify(creditManagementService, never()).captureReservedCredit(any());
        
        verify(creditTransactionService, never()).failProjectGenerationReservation(any(), any());
        verify(creditTransactionService, never()).recordRefund(any(), any(), any());
    }
}
