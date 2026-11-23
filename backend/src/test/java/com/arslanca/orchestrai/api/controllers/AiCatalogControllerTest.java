package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.HeavyModelCatalogService;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelCatalogResponse;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.LightModelDescriptor;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCatalogControllerTest {

    @Mock
    private HeavyModelCatalogService heavyModelCatalogService;

    private AiCatalogController aiCatalogController;

    @BeforeEach
    void setUp() {
        aiCatalogController = new AiCatalogController(heavyModelCatalogService);
    }

    @Test
    void shouldReturnCatalogPayload() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), "memet", UserRole.USER, null);
        HeavyModelCatalogResponse response = new HeavyModelCatalogResponse(
                new LightModelDescriptor("google", "gemini-1.5-flash", "Gemini 1.5 Flash", true),
                List.of(new HeavyModelDescriptor("google:gemini-3-pro", "google", "gemini-3-pro", "Gemini 3 Pro", "PLATFORM", true, true, false, false, null, null, null, List.of())));
        when(heavyModelCatalogService.getCatalog(authenticatedUser.id())).thenReturn(response);

        var entity = aiCatalogController.getCatalog(authenticatedUser);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().catalogModels()).isEqualTo(response.catalogModels());
    }
}
