package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.project.ProjectResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;

import java.util.UUID;

/**
 * Legacy project generation entrypoint and persisted project access contract.
 *
 * <p>
 * `generateBlueprint` is a compatibility wrapper that routes legacy callers
 * into the canonical preparation and reviewed-start lifecycle.
 * </p>
 */
public interface ProjectGenerationService {

    /**
     * Starts a generation run for legacy callers by creating a preparation
     * snapshot and delegating to the canonical reviewed pipeline.
     *
     * @param userId  JWT'den gelen kullanıcı UUID'si
     * @param request legacy project generation request
     * @return persisted project id
     * @throws com.arslanca.orchestrai.core.exception.type.InsufficientCreditException yetersiz
     *                                                                                 kredi
     */
    UUID generateBlueprint(UUID userId, ProjectRequest request, SseEventPublisher publisher);

    /**
     * Kullanıcının proje geçmişini sayfalı olarak döner.
     *
     * @param userId   JWT'den gelen kullanıcı UUID'si
     * @param pageable sayfalama parametreleri
     * @return proje listesi sayfa
     */
    Page<ProjectResponse> getUserProjects(UUID userId, Pageable pageable);

    /**
     * Tekil proje detayını döner.
     *
     * <p>
     * Yetkilendirme kontrolü bu metot içinde yapılır:
     * proje başka bir kullanıcıya aitse {@code IllegalStateException} fırlatılır.
     * </p>
     *
     * @param userId    JWT'den gelen kullanıcı UUID'si
     * @param projectId erişilmek istenen proje UUID'si
     * @return proje detay DTO'su
     * @throws IllegalStateException proje bulunamazsa veya erişim yetkisi yoksa
     */
    ProjectResponse getProjectById(UUID userId, UUID projectId);

    /**
     * Tekil proje artifact agacini doner.
     * Ownership kontrolu yapilir.
     */
    com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse getProjectArtifact(UUID userId, UUID projectId);

    /**
     * Kullanıcının projesini siler.
     * Ownership kontrolü yapılır — kullanıcı sadece kendi projesini silebilir.
     */
    void deleteProject(UUID userId, UUID projectId);
}
