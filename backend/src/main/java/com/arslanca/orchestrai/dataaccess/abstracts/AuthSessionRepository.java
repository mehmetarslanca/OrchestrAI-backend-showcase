package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.AuthSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSessionEntity session where session.tokenHash = :tokenHash")
    Optional<AuthSessionEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<AuthSessionEntity> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    void deleteAllByExpiresAtBefore(OffsetDateTime expiresAt);
}
