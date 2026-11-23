package com.arslanca.orchestrai.business.dtos.ai;

public enum CatalogModelAvailabilityState {
    SELECTABLE,
    LOCKED_CONNECTION_REQUIRED,
    REAUTH_REQUIRED,
    ENTITLEMENT_MISSING,
    UNSUPPORTED,
    DEFERRED
}
