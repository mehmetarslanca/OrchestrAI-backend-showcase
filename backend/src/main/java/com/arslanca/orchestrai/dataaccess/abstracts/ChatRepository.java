package com.arslanca.orchestrai.dataaccess.abstracts;

import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    List<ChatEntity> findByConversationProjectIdOrderByCreatedAtDesc(UUID conversationProjectId);

    /**
     * Chat'i ID ile bulur ve ownership doğrulaması için
     * konuşma projesinin sahibi olan kullanıcıyı kontrol eder.
     */
    @Query("SELECT c FROM ChatEntity c JOIN c.conversationProject cp WHERE c.id = :chatId AND cp.user.id = :userId")
    Optional<ChatEntity> findByIdAndUserId(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("SELECT c FROM ChatEntity c JOIN FETCH c.conversationProject cp JOIN cp.user u WHERE c.id = :chatId AND u.id = :userId")
    Optional<ChatEntity> findByIdAndUserIdWithProject(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatEntity c JOIN c.conversationProject cp WHERE c.id = :chatId AND cp.user.id = :userId")
    Optional<ChatEntity> findByIdAndUserIdForUpdate(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("SELECT cp.user.id FROM ChatEntity c JOIN c.conversationProject cp WHERE c.id = :chatId")
    Optional<UUID> findOwnerUserIdByChatId(@Param("chatId") UUID chatId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE ChatEntity c SET c.title = :title, c.isAutoNamed = :isAutoNamed WHERE c.id = :id AND c.isAutoNamed = true")
    int updateTitleIfAutoNamed(@Param("id") UUID id, @Param("title") String title, @Param("isAutoNamed") boolean isAutoNamed);
}
