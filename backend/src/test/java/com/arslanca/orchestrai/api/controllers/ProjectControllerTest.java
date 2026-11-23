package com.arslanca.orchestrai.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationService;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.project.ProjectResponse;
import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.UserRole;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectGenerationService projectGenerationService;

    private ProjectController controller;
    private AuthenticatedUser authenticatedUser;
    private ProjectGenerationFeatureProperties featureProperties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        featureProperties = new ProjectGenerationFeatureProperties();
        meterRegistry = new SimpleMeterRegistry();
        controller = new ProjectController(projectGenerationService, featureProperties, meterRegistry);
        authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), "memet", UserRole.USER, null);
    }

    @Test
    @DisplayName("generateBlueprint: gecerli istek ile SseEmitter doner")
    void shouldReturnSseEmitterForValidRequest() {
        final ProjectRequest request = new ProjectRequest("my-project", "cursor", "Build a SaaS", null);
        when(projectGenerationService.generateBlueprint(eq(authenticatedUser.id()), eq(request), any(SseEventPublisher.class)))
                .thenReturn(UUID.randomUUID());

        final SseEmitter result = controller.generateBlueprint(authenticatedUser, request);

        assertThat(result).isNotNull();
        verify(projectGenerationService).generateBlueprint(eq(authenticatedUser.id()), eq(request), any(SseEventPublisher.class));
        assertThat(meterRegistry.counter(
                "project_generation.legacy.endpoint",
                "endpoint", "projects_generate",
                "outcome", "requested").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("generateBlueprint: legacy wrapper flag kapaliysa 404 doner")
    void shouldRejectLegacyGenerationWhenFeatureFlagDisabled() {
        featureProperties.setLegacyGenerateEnabled(false);

        assertThatThrownBy(() -> controller.generateBlueprint(
                authenticatedUser,
                new ProjectRequest("my-project", "cursor", "Build a SaaS", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(meterRegistry.counter(
                "project_generation.legacy.endpoint",
                "endpoint", "projects_generate",
                "outcome", "disabled").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("getUserProjects: kullanicinin proje listesini sayfali olarak doner ve 200 OK")
    void shouldReturnPagedProjectsWithOkStatus() {
        final PageRequest pageable = PageRequest.of(0, 20);
        final ProjectResponse projectResponse = new ProjectResponse(
                UUID.randomUUID(), "my-project", "cursor",
                "COMPLETED", "original prompt", java.util.Map.of(), OffsetDateTime.now());
        final Page<ProjectResponse> page = new PageImpl<>(List.of(projectResponse));

        when(projectGenerationService.getUserProjects(authenticatedUser.id(), pageable)).thenReturn(page);

        final var response = controller.getUserProjects(authenticatedUser, pageable);

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getProjectById: mevcut proje icin 200 OK doner")
    void shouldReturnProjectWithOkStatus() {
        final UUID projectId = UUID.randomUUID();
        final ProjectResponse projectResponse = new ProjectResponse(
                projectId, "my-project", "cursor",
                "COMPLETED", "original prompt", java.util.Map.of(), OffsetDateTime.now());

        when(projectGenerationService.getProjectById(authenticatedUser.id(), projectId)).thenReturn(projectResponse);

        final var response = controller.getProjectById(authenticatedUser, projectId);

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).isEqualTo(projectResponse);
    }

    @Test
    @DisplayName("getProjectById: servis exception firlatirsa exception yayilir")
    void shouldPropagateExceptionFromService() {
        final UUID projectId = UUID.randomUUID();
        when(projectGenerationService.getProjectById(authenticatedUser.id(), projectId))
                .thenThrow(new com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException("Proje bulunamadi"));

        assertThatThrownBy(() -> controller.getProjectById(authenticatedUser, projectId))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException.class)
                .hasMessage("Proje bulunamadi");
    }

    @Test
    @DisplayName("deleteProject: basarili silme islemi 204 No Content doner")
    void shouldReturnNoContentOnDelete() {
        final UUID projectId = UUID.randomUUID();
        doNothing().when(projectGenerationService).deleteProject(authenticatedUser.id(), projectId);

        final var response = controller.deleteProject(authenticatedUser, projectId);

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.NO_CONTENT.value());
        verify(projectGenerationService).deleteProject(authenticatedUser.id(), projectId);
    }
}
