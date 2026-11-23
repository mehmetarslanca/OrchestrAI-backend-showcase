package com.arslanca.orchestrai.core.exception.type;

/**
 * Kullanıcının yeterli kredisi olmadığında fırlatılır.
 *
 * <p>
 * {@link com.arslanca.orchestrai.business.concretes.CreditManager#deductCredit(java.util.UUID)}
 * tarafından fırlatılır ve
 * {@link com.arslanca.orchestrai.core.exception.GlobalExceptionHandler}
 * tarafından 409 CONFLICT olarak işlenir.
 * </p>
 */
public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException(final String message) {
        super(message);
    }

    public static InsufficientCreditException forUser(final java.util.UUID userId) {
        return new InsufficientCreditException(
                "Yetersiz kredi. Blueprint oluşturmak için kredi satın alınız. userId=" + userId);
    }
}
