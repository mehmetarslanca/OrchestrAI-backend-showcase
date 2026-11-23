package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationProjectRepository extends JpaRepository<ConversationProjectEntity, UUID> {

    List<ConversationProjectEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ConversationProjectEntity> findByIdAndUserId(UUID id, UUID userId);

    void deleteAllByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE ConversationProjectEntity p SET p.name = :name, p.isAutoNamed = :isAutoNamed WHERE p.id = :id AND p.isAutoNamed = true")
    int updateNameIfAutoNamed(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("name") String name, @org.springframework.data.repository.query.Param("isAutoNamed") boolean isAutoNamed);
}
