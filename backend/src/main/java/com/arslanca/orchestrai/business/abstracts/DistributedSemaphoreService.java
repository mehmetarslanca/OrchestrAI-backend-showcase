package com.arslanca.orchestrai.business.abstracts;

import java.util.Set;

public interface DistributedSemaphoreService {
    boolean tryAcquire(String resource, String leaseToken);
    void release(String resource, String leaseToken);
    Set<String> getActiveLeases(String resource);
}
