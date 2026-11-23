package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.core.exception.type.InsufficientCreditException;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kredi yönetimi konkret implementasyonu.
 *
 * <p>
 * Kredi düşme işlemi pessimistic write lock ile race condition'a karşı korunur.
 * Yetersiz kredi durumunda entity'nin domain metodu
 * {@link com.arslanca.orchestrai.entity.concretes.CreditEntity#deductCredit()}
 * {@code IllegalStateException} fırlatır; bu manager onu
 * {@link InsufficientCreditException}'a
 * dönüştürerek GlobalExceptionHandler'ın 409 response'u doğru şekilde
 * oluşturmasını sağlar.
 * </p>
 */
@Service
@Slf4j
public class CreditManager implements CreditManagementService {

  private final CreditRepository creditRepository;
  private final EntityMapper entityMapper;

    public CreditManager(CreditRepository creditRepository, EntityMapper entityMapper) {
        this.creditRepository = creditRepository;
        this.entityMapper = entityMapper;
    }


  /**
   * {@inheritDoc}
   *
   * <p>
   * Read-only transaction ile kredi bakiyesini döner.
   * </p>
   */
  @Override
  @Transactional(readOnly = true)
  public CreditResponse getCreditBalance(final UUID userId) {
    log.debug("Fetching credit balance for userId={}", userId);
    return creditRepository.findByUserId(userId)
        .map(entityMapper::toCreditResponse)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydı bulunamadı: userId=" + userId));
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Pessimistic write lock (SELECT FOR UPDATE) ile çalışır.
   * Aynı kullanıcı için eş zamanlı iki istek geldiğinde, ikincisi
   * birincisinin commit'ini bekler — bu verinin tutarlı kalmasını sağlar.
   * </p>
   *
   * <p>
   * {@link com.arslanca.orchestrai.entity.concretes.CreditEntity#deductCredit()}
   * domain metodu kredi yeterliliğini kontrol eder ve yetersizse
   * {@code IllegalStateException} fırlatır. Bu manager bu exception'ı
   * {@link InsufficientCreditException}'a wrap eder, böylece
   * GlobalExceptionHandler
   * doğru HTTP status (409 INSUFFICIENT_CREDIT) döner.
   * </p>
   */
  @Override
  @Transactional
  public void deductCredit(final UUID userId) {
    log.info("Deducting credit for userId={}", userId);
    final var credit = creditRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydı bulunamadı: userId=" + userId));

    try {
      credit.deductCredit();
    } catch (IllegalStateException e) {
      throw new InsufficientCreditException("Yetersiz kredi: userId=" + userId);
    }

    creditRepository.save(credit);
    log.info("Credit deducted successfully for userId={}. Remaining balance: {}", userId, credit.getBalance());
  }

  @Override
  @Transactional
  public void reserveCredit(final UUID userId) {
    log.info("Reserving credit for userId={}", userId);
    final var credit = creditRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydi bulunamadi: userId=" + userId));

    try {
      credit.reserveCredit();
    } catch (IllegalStateException e) {
      throw new InsufficientCreditException("Yetersiz kredi: userId=" + userId);
    }

    creditRepository.save(credit);
    log.info("Credit reserved successfully for userId={}. Available balance: {}, reserved={}",
        userId,
        credit.getAvailableBalance(),
        credit.getReservedCredits());
  }

  @Override
  @Transactional
  public void captureReservedCredit(final UUID userId) {
    log.info("Capturing reserved credit for userId={}", userId);
    final var credit = creditRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydi bulunamadi: userId=" + userId));

    credit.captureReservedCredit();
    creditRepository.save(credit);
    log.info("Reserved credit captured for userId={}. Remaining balance: {}, reserved={}",
        userId,
        credit.getBalance(),
        credit.getReservedCredits());
  }

  @Override
  @Transactional
  public void releaseReservedCredit(final UUID userId) {
    log.info("Releasing reserved credit for userId={}", userId);
    final var credit = creditRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydi bulunamadi: userId=" + userId));

    credit.releaseReservedCredit();
    creditRepository.save(credit);
    log.info("Reserved credit released for userId={}. Available balance: {}, reserved={}",
        userId,
        credit.getAvailableBalance(),
        credit.getReservedCredits());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Transactional
  public void refundCredit(final UUID userId) {
    log.info("Refunding credit for userId={}", userId);
    final var credit = creditRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new IllegalStateException("Kredi kaydı bulunamadı: userId=" + userId));

    credit.addCredits(1);
    creditRepository.save(credit);
    log.info("Credit refunded successfully for userId={}. Remaining balance: {}", userId, credit.getBalance());
  }
}
