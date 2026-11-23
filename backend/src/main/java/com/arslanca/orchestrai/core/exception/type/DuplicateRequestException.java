package com.arslanca.orchestrai.core.exception.type;

/**
 * Aynı idempotency key ile gelen duplicate blueprint generation isteği
 * fırlatılır.
 *
 * <p>
 * CLI tarafından her istek için üretilen {@code X-Idempotency-Key} header'ı
 * kontrol edilir.
 * Aynı key içinde zaten bir istek işleniyorsa veya yakın zamanda tamamlandıysa
 * bu exception fırlatılır.
 * </p>
 *
 * <p>
 * {@link com.arslanca.orchestrai.core.exception.GlobalExceptionHandler}
 * tarafından
 * 409 CONFLICT olarak işlenir.
 * </p>
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(final String idempotencyKey) {
        super("Bu istek zaten işleniyor veya yakın zamanda tamamlandı. "
                + "Lütfen yeni bir istek başlatın. key=" + idempotencyKey);
    }
}
