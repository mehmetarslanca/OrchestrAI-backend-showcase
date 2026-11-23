package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.ProjectArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectArtifactRepository extends JpaRepository<ProjectArtifactEntity, UUID> {

    Optional<ProjectArtifactEntity> findByProjectIdAndProject_User_Id(UUID projectId, UUID userId);
}
