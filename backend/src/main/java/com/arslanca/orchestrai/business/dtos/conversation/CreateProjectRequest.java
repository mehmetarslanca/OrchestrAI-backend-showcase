package com.arslanca.orchestrai.business.dtos.conversation;

import jakarta.validation.constraints.Size;

/**
 * Yeni konuşma projesi oluşturma isteği.
 */
public record CreateProjectRequest(
        @Size(max = 200, message = "Proje adı en fazla 200 karakter olabilir")
        String name
) {
}
