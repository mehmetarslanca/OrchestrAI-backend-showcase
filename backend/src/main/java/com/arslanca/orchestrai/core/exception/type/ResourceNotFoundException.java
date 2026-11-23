package com.arslanca.orchestrai.core.exception.type;

/**
 * Kaynak bulunamadığında fırlatılır.
 *
 * <p>
 * {@link com.arslanca.orchestrai.core.exception.GlobalExceptionHandler}
 * tarafından {@code 404 NOT_FOUND} olarak yanıtlanır.
 * </p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(final String message) {
        super(message);
    }

    /**
     * Proje bulunamadığında veya erişim yetkisi olmadığında kullanılır.
     */
    public static ResourceNotFoundException forProject(final java.util.UUID projectId) {
        return new ResourceNotFoundException("Proje bulunamadı veya erişim yetkisi yok: projectId=" + projectId);
    }
}
