package com.arslanca.orchestrai.business.concretes.cancellation;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException;

public class GenerationCancellationToken {

    private final UUID projectId;
    private final Supplier<GenerationFailureReason> externalCancellationReasonSupplier;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final AtomicReference<GenerationFailureReason> cancellationReason = new AtomicReference<>();
    private volatile Thread executionThread;

    public GenerationCancellationToken(final UUID projectId) {
        this(projectId, () -> null);
    }

    public GenerationCancellationToken(
            final UUID projectId,
            final Supplier<GenerationFailureReason> externalCancellationReasonSupplier) {
        this.projectId = projectId;
        this.externalCancellationReasonSupplier = externalCancellationReasonSupplier;
    }

    public void bindThread(Thread thread) {
        this.executionThread = thread;
    }

    public boolean cancel(final GenerationFailureReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        final boolean updated = cancellationRequested.compareAndSet(false, true);
        if (updated) {
            cancellationReason.set(reason);
            if (executionThread != null && executionThread.isAlive()) {
                executionThread.interrupt();
            }
        }
        return updated;
    }

    public boolean isCancellationRequested() {
        syncExternalCancellation();
        return cancellationRequested.get();
    }

    public GenerationFailureReason cancellationReason() {
        syncExternalCancellation();
        return cancellationReason.get();
    }

    public UUID projectId() {
        return projectId;
    }

    public void throwIfCancellationRequested() {
        syncExternalCancellation();
        if (isCancellationRequested()) {
            throw new ProjectGenerationCancelledException(projectId, cancellationReason());
        }
    }

    private void syncExternalCancellation() {
        if (cancellationRequested.get() || externalCancellationReasonSupplier == null) {
            return;
        }

        final GenerationFailureReason externalReason = externalCancellationReasonSupplier.get();
        if (externalReason != null) {
            cancel(externalReason);
        }
    }
}
