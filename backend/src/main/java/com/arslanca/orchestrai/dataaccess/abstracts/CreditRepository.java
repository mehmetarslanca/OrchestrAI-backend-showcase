package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.CreditEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditRepository extends JpaRepository<CreditEntity, UUID> {

    Optional<CreditEntity> findByUserId(UUID userId);

    /**
     * Pessimistic write lock ile kredi kaydını getirir.
     * Concurrent kredi düşürme race condition'larını önler.
     * SADECE bir transaction (@Transactional) içinde kullanılmalıdır.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditEntity c WHERE c.user.id = :userId")
    Optional<CreditEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
