package com.arslanca.orchestrai.entity.concretes;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CreditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "balance", nullable = false)
    @Builder.Default
    private int balance = 5;

    @Column(name = "total_used", nullable = false)
    @Builder.Default
    private int totalUsed = 0;

    @Column(name = "reserved_credits", nullable = false)
    @Builder.Default
    private int reservedCredits = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ---- Domain Methods ----

    /**
     * Kredi yeterliliğini kontrol eder.
     * 
     * @return balance > 0 ise true
     */
    public boolean hasAvailableCredit() {
        return getAvailableBalance() > 0;
    }

    public int getAvailableBalance() {
        return this.balance - this.reservedCredits;
    }

    /**
     * Bir kredi düşer. Yetersiz kredi varsa InsufficientCreditException fırlatır.
     * Bu method SADECE bir transaction içinde çağrılmalıdır.
     */
    public void deductCredit() {
        if (!hasAvailableCredit()) {
            throw com.arslanca.orchestrai.core.exception.type.InsufficientCreditException
                    .forUser(this.user != null ? this.user.getId() : null);
        }
        this.balance--;
        this.totalUsed++;
    }

    public void reserveCredit() {
        if (!hasAvailableCredit()) {
            throw com.arslanca.orchestrai.core.exception.type.InsufficientCreditException
                    .forUser(this.user != null ? this.user.getId() : null);
        }
        this.reservedCredits++;
    }

    public void captureReservedCredit() {
        if (this.reservedCredits <= 0) {
            throw new IllegalStateException("Ayrilmis kredi bulunamadi.");
        }
        this.reservedCredits--;
        this.balance--;
        this.totalUsed++;
    }

    public void releaseReservedCredit() {
        if (this.reservedCredits <= 0) {
            throw new IllegalStateException("Serbest birakilacak ayrilmis kredi bulunamadi.");
        }
        this.reservedCredits--;
    }

    /**
     * Belirtilen miktarda kredi ekler (admin veya satın alma işlemi).
     * 
     * @param amount eklenecek kredi miktarı (pozitif olmalı)
     */
    public void addCredits(final int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Kredi miktarı pozitif olmalıdır: " + amount);
        }
        this.balance += amount;
    }

    /**
     * Set the user for bi-directional relationship management.
     */
    public void setUser(UserEntity user) {
        this.user = user;
    }
}
