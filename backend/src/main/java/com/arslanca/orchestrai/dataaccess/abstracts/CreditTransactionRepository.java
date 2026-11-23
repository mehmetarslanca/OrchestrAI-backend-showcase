package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.CreditTransactionEntity;
import com.arslanca.orchestrai.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Optional;

public interface CreditTransactionRepository extends JpaRepository<CreditTransactionEntity, UUID> {
    Page<CreditTransactionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    void deleteAllByUserId(UUID userId);
    boolean existsByReferenceIdAndType(String referenceId, TransactionType type);
    Optional<CreditTransactionEntity> findByReferenceIdAndType(String referenceId, TransactionType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CreditTransactionEntity t WHERE t.referenceId = :referenceId AND t.type = :type")
    Optional<CreditTransactionEntity> findByReferenceIdAndTypeForUpdate(
            @Param("referenceId") String referenceId,
            @Param("type") TransactionType type);
}
