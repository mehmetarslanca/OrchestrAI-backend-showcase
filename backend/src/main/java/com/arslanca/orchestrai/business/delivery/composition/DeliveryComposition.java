package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifest;

import java.util.List;

public record DeliveryComposition(
        String projectName,
        List<ComposedDocument> documents,
        DeliveryManifest manifest) {

    public DeliveryComposition {
        projectName = projectName == null ? "" : projectName.trim();
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
