package com.arslanca.orchestrai.business.dtos.orchestration;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseMessage<T>(
        String type,
        String message,
        T data) {
    public static SseMessage<Object> info(String message) {
        return SseMessage.<Object>builder()
                .type("INFO")
                .message(message)
                .build();
    }

    public static <T> SseMessage<Object> completed(T data) {
        return SseMessage.<Object>builder()
                .type("COMPLETED")
                .data(data)
                .build();
    }

    public static SseMessage<Object> error(String message) {
        return SseMessage.<Object>builder()
                .type("ERROR")
                .message(message)
                .build();
    }
}
