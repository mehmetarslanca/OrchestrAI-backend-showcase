package com.arslanca.orchestrai.business.mapper;

import com.arslanca.orchestrai.entity.concretes.CreditEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectArtifactEntity;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.dtos.project.ProjectResponse;
import com.arslanca.orchestrai.business.dtos.project.ProjectArtifactResponse;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EntityMapper {

    @Mapping(target = "role", expression = "java(entity.getRole() != null ? entity.getRole().name() : null)")
    @Mapping(target = "provider", expression = "java(entity.getProvider() != null ? entity.getProvider().name() : null)")
    UserResponse toUserResponse(UserEntity entity);

    @Mapping(target = "balance", expression = "java(entity.getAvailableBalance())")
    CreditResponse toCreditResponse(CreditEntity entity);

    @Mapping(target = "ideTarget", expression = "java(entity.getIdeTarget() != null ? entity.getIdeTarget().name().toLowerCase() : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    ProjectResponse toProjectResponse(ProjectEntity entity);

    @Mapping(target = "projectId", source = "projectId")
    ProjectArtifactResponse toProjectArtifactResponse(ProjectArtifactEntity entity);
}
