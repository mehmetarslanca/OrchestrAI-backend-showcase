package com.arslanca.orchestrai.business.abstracts;

import java.util.List;
import java.util.Optional;

import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;

public interface StarterCatalogService {

    List<StarterManifest> getApprovedStarters();

    Optional<StarterManifest> findByKey(String key);
}
