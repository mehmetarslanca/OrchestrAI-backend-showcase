package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;

import java.util.UUID;

/**
 * Kredi yönetimi için servis sözleşmesi.
 *
 * <p>
 * Kullanıcının mevcut kredi durumunu sorgular ve blueprint oluşturma sırasında
 * pessimistic lock altında kredi düşme işlemini yürütür.
 * İş mantığı detayları {@code CreditManager} implementasyonunda.
 * </p>
 */
public interface CreditManagementService {

    /**
     * Kullanıcının mevcut kredi bakiyesini döner.
     *
     * @param userId JWT'den gelen kullanıcı UUID'si
     * @return kredi durumu DTO'su
     * @throws IllegalStateException kredi kaydı bulunamazsa
     */
    CreditResponse getCreditBalance(UUID userId);

    /**
     * Bir krediyi düşer. Pessimistic write lock ile çalışır.
     *
     * <p>
     * Bu metot mutlaka bir {@code @Transactional} bağlamında çağrılmalıdır.
     * Concurrent duplicate isteklerde race condition'ı önlemek için
     * veritabanı satır kilidi (SELECT FOR UPDATE) kullanır.
     * </p>
     *
     * @param userId JWT'den gelen kullanıcı UUID'si
     * @throws com.arslanca.orchestrai.core.exception.type.InsufficientCreditException yetersiz
     *                                                                                 kredi
     *                                                                                 varsa
     * @throws IllegalStateException                                                   kredi
     *                                                                                 kaydı
     *                                                                                 bulunamazsa
     */
    void deductCredit(UUID userId);

    /**
     * Project generation için bir krediyi reserve eder.
     */
    void reserveCredit(UUID userId);

    /**
     * Daha once reserve edilen krediyi capture eder.
     */
    void captureReservedCredit(UUID userId);

    /**
     * Daha once reserve edilen krediyi serbest birakir.
     */
    void releaseReservedCredit(UUID userId);

    /**
     * LLM hatası durumunda tahsil edilen krediyi kullanıcıya iade eder.
     *
     * @param userId JWT'den gelen kullanıcı UUID'si
     */
    void refundCredit(UUID userId);
}
