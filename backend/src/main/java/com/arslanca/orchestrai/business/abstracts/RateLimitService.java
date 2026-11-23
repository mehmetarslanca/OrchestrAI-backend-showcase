package com.arslanca.orchestrai.business.abstracts;

import java.util.UUID;

public interface RateLimitService {
    /**
     * Checks if the user is allowed to make a request.
     * @param userId The ID of the authenticated user
     * @return true if allowed, false if rate limited
     */
    boolean tryAcquire(UUID userId);
}
