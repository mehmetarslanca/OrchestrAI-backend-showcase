package com.arslanca.orchestrai.business.planning.lifecycle;

import com.arslanca.orchestrai.core.exception.type.PreparationStateException;

public class LifecycleViolationException extends PreparationStateException {

    private final LifecycleViolationReason reason;

    public LifecycleViolationException(final LifecycleViolationReason reason, final String message) {
        super(message);
        this.reason = reason;
    }

    public LifecycleViolationReason reason() {
        return reason;
    }
}
