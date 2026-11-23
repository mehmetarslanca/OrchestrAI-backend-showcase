package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAiConnectionRepository extends JpaRepository<UserAiConnectionEntity, UUID> {

    Optional<UserAiConnectionEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserAiConnectionEntity> findByUserIdAndConnectionType(UUID userId, AiConnectionType connectionType);

    List<UserAiConnectionEntity> findAllByUserId(UUID userId);

    List<UserAiConnectionEntity> findAllByUserIdAndConnectionStatusIn(UUID userId, Collection<AiConnectionStatus> statuses);
}
