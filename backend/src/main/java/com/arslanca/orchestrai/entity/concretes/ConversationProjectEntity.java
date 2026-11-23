package com.arslanca.orchestrai.entity.concretes;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kullanıcının konuşma projelerini temsil eder.
 *
 * <p>
 * Her kullanıcı birden fazla konuşma projesi oluşturabilir.
 * Her proje altında birden fazla chat session'ı bulunabilir.
 * </p>
 */
@Entity
@Table(name = "conversation_projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ConversationProjectEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @OneToMany(mappedBy = "conversationProject", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt DESC")
  @Builder.Default
  private List<ChatEntity> chats = new ArrayList<>();

  @Column(name = "is_auto_named", nullable = false)
  @Builder.Default
  private boolean isAutoNamed = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // ---- Domain Methods ----

  /**
   * Konuşma projesinin adını günceller.
   */
  public void rename(final String newName, final boolean isAutoNamed) {
    if (newName == null || newName.isBlank()) {
      throw new IllegalArgumentException("Proje adı boş olamaz");
    }
    this.name = newName;
    this.isAutoNamed = isAutoNamed;
  }
}
