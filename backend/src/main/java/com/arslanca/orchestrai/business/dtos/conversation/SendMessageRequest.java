package com.arslanca.orchestrai.business.dtos.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Chat'e mesaj gönderme isteği.
 */
public record SendMessageRequest(
        @NotBlank(message = "Mesaj içeriği boş olamaz")
        @Size(max = 8000, message = "Mesaj en fazla 8000 karakter olabilir")
        String content,

        /**
         * Optional IDE preference carried alongside chat messages.
         * Chat streaming itself does not auto-start generation; actual generation
         * still flows through explicit prepare/start endpoints.
         */
        @Pattern(regexp = "cursor|antigravity|windsurf|generic",
                 message = "ideTarget must be one of: cursor, antigravity, windsurf, generic")
        String ideTarget
) {
    /**
     * Compact constructor — applies default ideTarget when not provided.
     */
    public SendMessageRequest {
        if (ideTarget == null || ideTarget.isBlank()) {
            ideTarget = "generic";
        }
    }
}
