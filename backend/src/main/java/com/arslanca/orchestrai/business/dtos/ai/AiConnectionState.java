package com.arslanca.orchestrai.business.dtos.ai;

public enum AiConnectionState {
    CONNECTED,
    MISSING,
    REAUTH_REQUIRED,
    ENTITLEMENT_MISSING,
    INVALID,
    PENDING,
    PLATFORM_MANAGED,
    UNSUPPORTED,
    DEFERRED
}
