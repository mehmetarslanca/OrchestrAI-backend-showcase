package com.arslanca.orchestrai.entity.concretes;

import com.arslanca.orchestrai.entity.enums.OAuthProvider;
import com.arslanca.orchestrai.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "github_id", unique = true)
    private Long githubId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    @Builder.Default
    private OAuthProvider provider = OAuthProvider.GITHUB;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ---- Relationship: 1:1 Credit ----
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CreditEntity credit;

    /**
     * İlk kayıt sırasında kredi kaydını user'a bağlar ve CreditEntity içerisindeki
     * user referansını da günceller (bi-directional consistency).
     * CascadeType.ALL sayesinde credit de otomatik persist olur.
     */
    public void assignInitialCredit(final CreditEntity creditEntity) {
        this.credit = creditEntity;
        if (creditEntity != null) {
            creditEntity.setUser(this);
        }
    }

    // ---- Domain Methods ----

    /**
     * Kullanıcı profilini GitHub'dan gelen güncel verilerle günceller.
     * Entity'nin state'ini değiştiren tek sınırlı method.
     */
    public void updateProfile(final String username, final String email, final String avatarUrl) {
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }
}
