package com.arslanca.orchestrai.business.delivery.policy;

public interface ReservedPathPolicy {
    boolean isReserved(String path);
}