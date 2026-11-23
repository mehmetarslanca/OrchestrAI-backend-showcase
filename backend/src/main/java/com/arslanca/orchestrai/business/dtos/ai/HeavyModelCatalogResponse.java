package com.arslanca.orchestrai.business.dtos.ai;

import java.util.List;

public record HeavyModelCatalogResponse(
        LightModelDescriptor lightModel,
        List<HeavyModelDescriptor> heavyModels,
        List<HeavyModelDescriptor> catalogModels) {

    public HeavyModelCatalogResponse {
        heavyModels = heavyModels == null ? List.of() : List.copyOf(heavyModels);
        catalogModels = catalogModels == null ? List.of() : List.copyOf(catalogModels);
    }

    public HeavyModelCatalogResponse(final LightModelDescriptor lightModel, final List<HeavyModelDescriptor> heavyModels) {
        this(lightModel, heavyModels, heavyModels);
    }
}
