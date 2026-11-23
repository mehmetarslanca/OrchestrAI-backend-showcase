package com.arslanca.orchestrai.api.adapters;

import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Adapter that implements the SseEventPublisher domain interface using Spring
 * MVC SseEmitter.
 */
@Slf4j
public class SseEmitterAdapter implements SseEventPublisher {

    private final SseEmitter emitter;

    public SseEmitterAdapter(SseEmitter emitter) {
        this.emitter = emitter;
    }


    @Override
    public void sendEvent(final SseProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
        } catch (final IOException e) {
            log.warn("SSE event gönderilemedi (type={}): {}", event.type(), e.getMessage());
            throw new RuntimeException("SSE bağlantısı koptu", e);
        }
    }

    @Override
    public void complete() {
        try {
            emitter.complete();
        } catch (final Exception e) {
            log.error("Failed to complete emitter: {}", e.getMessage());
        }
    }

    @Override
    public void completeWithError(final Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (final Exception e) {
            log.error("Failed to complete emitter with error: {}", e.getMessage());
        }
    }

    @Override
    public void onCompletion(final Runnable callback) {
        emitter.onCompletion(callback);
    }

    @Override
    public void onTimeout(final Runnable callback) {
        emitter.onTimeout(callback);
    }

    @Override
    public void onError(final Consumer<Throwable> callback) {
        emitter.onError(callback);
    }
}
