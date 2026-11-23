package com.arslanca.orchestrai.business.abstracts.delivery;

import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;

import java.util.function.Consumer;

/**
 * Abstraction for sending SSE progress events, decoupling core business logic
 * from protocol specifics.
 */
public interface SseEventPublisher {

    void sendEvent(SseProgressEvent event);

    void complete();

    void completeWithError(Throwable error);

    default void onCompletion(final Runnable callback) {
    }

    default void onTimeout(final Runnable callback) {
    }

    default void onError(final Consumer<Throwable> callback) {
    }
}
