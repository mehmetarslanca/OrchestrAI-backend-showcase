package com.arslanca.orchestrai.dataaccess.abstracts;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.arslanca.orchestrai.entity.concretes.ChatPlanningSnapshotEntity;

public interface ChatPlanningSnapshotRepository extends JpaRepository<ChatPlanningSnapshotEntity, UUID> {

    @Query("SELECT s FROM ChatPlanningSnapshotEntity s WHERE s.chat.id = :chatId")
    Optional<ChatPlanningSnapshotEntity> findByChatId(@Param("chatId") UUID chatId);
}
