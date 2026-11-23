package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

import java.util.UUID;

public interface AiExecutionRouteResolverService {

    ResolvedAiExecutionRoute resolve(UUID userId, String requestedHeavyModelKey, CredentialSource requestedAuthSource);
}
