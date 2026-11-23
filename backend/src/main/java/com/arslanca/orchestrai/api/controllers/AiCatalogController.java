package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.HeavyModelCatalogService;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelCatalogResponse;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiCatalogController {

    private final HeavyModelCatalogService heavyModelCatalogService;

    public AiCatalogController(final HeavyModelCatalogService heavyModelCatalogService) {
        this.heavyModelCatalogService = heavyModelCatalogService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<HeavyModelCatalogResponse> getCatalog(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(heavyModelCatalogService.getCatalog(
                authenticatedUser != null ? authenticatedUser.id() : null));
    }
}
