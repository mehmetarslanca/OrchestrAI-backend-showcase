package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.HeavyModelCatalogResponse;

import java.util.UUID;

public interface HeavyModelCatalogService {

    default HeavyModelCatalogResponse getCatalog() {
        return getCatalog(null);
    }

    HeavyModelCatalogResponse getCatalog(UUID userId);
}
