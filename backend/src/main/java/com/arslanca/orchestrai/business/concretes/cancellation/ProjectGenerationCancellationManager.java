package com.arslanca.orchestrai.business.concretes.cancellation;

import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationCancellationService;
import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

@Service
public class ProjectGenerationCancellationManager implements ProjectGenerationCancellationService {

    private final ProjectGenerationSessionRegistry registry;
    private final ProjectStateService projectStateService;
    private final MeterRegistry meterRegistry;

    public ProjectGenerationCancellationManager(
            final ProjectGenerationSessionRegistry registry,
            final ProjectStateService projectStateService,
            final MeterRegistry meterRegistry) {
        this.registry = registry;
        this.projectStateService = projectStateService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public GenerationCancellationToken openSession(final UUID projectId) {
        return registry.register(projectId);
    }

    @Override
    @Transactional
    public void cancelGeneration(final UUID projectId, final GenerationFailureReason reason) {
        if (!projectStateService.markCancellationRequested(projectId, reason)) {
            return;
        }
        recordCancellationMetric(reason);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registry.markCancelled(projectId, reason);
                }
            });
            return;
        }

        registry.markCancelled(projectId, reason);
    }

    @Override
    public void closeSession(final UUID projectId) {
        registry.unregister(projectId);
    }

    private void recordCancellationMetric(final GenerationFailureReason reason) {
        meterRegistry.counter(
                "project_generation.cancel.requested",
                "reason", sanitizeReason(reason))
                .increment();
    }

    private String sanitizeReason(final GenerationFailureReason reason) {
        if (reason == null) {
            return "unknown";
        }
        return reason.metadataValue().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
