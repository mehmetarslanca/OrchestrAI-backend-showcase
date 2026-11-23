package com.arslanca.orchestrai.entity.concretes;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bir konuşma projesi altındaki chat session'ını temsil eder.
 *
 * <p>
 * Her chat birden fazla mesaj (user/assistant çiftleri) içerir.
 * </p>
 */
@Entity
@Table(name = "chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_project_id", nullable = false)
    private ConversationProjectEntity conversationProject;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<MessageEntity> messages = new ArrayList<>();

    @Column(name = "is_auto_named", nullable = false)
    @Builder.Default
    private boolean isAutoNamed = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private com.arslanca.orchestrai.entity.enums.ChatStatus status;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = com.arslanca.orchestrai.entity.enums.ChatStatus.IDLE;
        }
    }

    // ---- Domain Methods ----

    /**
     * Chat başlığını günceller.
     */
    public void updateTitle(final String newTitle, final boolean isAutoNamed) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Chat başlığı boş olamaz");
        }
        this.title = newTitle;
        this.isAutoNamed = isAutoNamed;
    }

    public void markAsGenerating() {
        this.status = com.arslanca.orchestrai.entity.enums.ChatStatus.GENERATING;
    }

    public void markAsIdle() {
        this.status = com.arslanca.orchestrai.entity.enums.ChatStatus.IDLE;
    }
    
    public void markAsCancelled() {
        this.status = com.arslanca.orchestrai.entity.enums.ChatStatus.CANCELLED;
    }
}
