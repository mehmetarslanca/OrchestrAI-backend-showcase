package com.arslanca.orchestrai.api.adapters;

import com.arslanca.orchestrai.business.dtos.sse.SseProgressEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEmitterAdapterTest {

    @Test
    void shouldTranslateIoFailureToRuntimeException() {
        SseEmitter emitter = new SseEmitter() {
            @Override
            public synchronized void send(SseEventBuilder builder) throws IOException {
                throw new IOException("broken pipe");
            }
        };

        SseEmitterAdapter adapter = new SseEmitterAdapter(emitter);

        assertThatThrownBy(() -> adapter.sendEvent(SseProgressEvent.ping()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SSE bağlantısı koptu");
    }

    @Test
    void shouldCompleteWithoutThrowing() {
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitterAdapter adapter = new SseEmitterAdapter(emitter);

        assertThatCode(adapter::complete).doesNotThrowAnyException();
        assertThatCode(() -> adapter.completeWithError(new RuntimeException("boom"))).doesNotThrowAnyException();
    }

    private static final class RecordingEmitter extends SseEmitter {

        @Override
        public synchronized void complete() {
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
        }
    }
}
