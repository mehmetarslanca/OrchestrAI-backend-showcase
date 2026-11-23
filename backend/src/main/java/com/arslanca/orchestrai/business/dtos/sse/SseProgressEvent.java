package com.arslanca.orchestrai.business.dtos.sse;

import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStage;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE ile CLI'ya gönderilen ilerleme olayı.
 *
 * <p>
 * Pipeline boyunca farklı aşamaları temsil eder:
 * <ul>
 * <li>RUN_CREATED → Persist edilen generation run kimliği ve ilk durum bilgisi</li>
 * <li>STAGE_CHANGE → Pipeline aşama değişikliği bildirimi</li>
 * <li>PROGRESS → Belirli bir aşamadaki ilerleme mesajı ve yüzdesi</li>
 * <li>RESULT → Generation run tamamlandığında son sonuç</li>
 * <li>ERROR → İşlem başarısız olduğunda hata detayı</li>
 * <li>PING → Bağlantının canlı tutulması için periyodik kontrol sinyali</li>
 * </ul>
 * </p>
 *
 * <p>
 * Bu record, mevcut {@code SseMessage<T>} ile çakışmaz.
 * {@code SseMessage} basit INFO/COMPLETED/ERROR mesajlar için kullanılırken,
 * {@code SseProgressEvent} pipeline progress tracking için özelleştirilmiştir.
 * (Audit Section 3, Option B)
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseProgressEvent(
        String type,
        GenerationStage stage,
        String message,
        int progress,
        Object data) {

    public static SseProgressEvent runCreated(final Object data) {
        return new SseProgressEvent("RUN_CREATED", null, "Project generation run created", 0, data);
    }

    /**
     * Belirli bir aşamadaki ilerleme mesajı.
     *
     * @param stage    pipeline aşaması (ör. "ANALYZING", "GENERATING")
     * @param message  kullanıcıya gösterilecek mesaj
     * @param progress tamamlanma yüzdesi (0-100)
     */
    public static SseProgressEvent progress(final GenerationStage stage, final String message, final int progress) {
        return new SseProgressEvent("PROGRESS", stage, message, progress, null);
    }

    /**
     * Pipeline aşama değişikliği bildirimi.
     *
     * @param stage   yeni aşama adı
     * @param message açıklama mesajı
     */
    public static SseProgressEvent stageChange(final GenerationStage stage, final String message) {
        return new SseProgressEvent("STAGE_CHANGE", stage, message, 0, null);
    }

    /**
     * Generation run tamamlandığında gönderilen son event.
     *
     * @param data canonical result payload
     */
    public static SseProgressEvent result(final Object data) {
        return new SseProgressEvent("RESULT", GenerationStage.COMPLETE, "Project generation completed", 100, data);
    }

    /**
     * Hata durumunda gönderilen event.
     *
     * @param message hata mesajı
     */
    public static SseProgressEvent error(final String message) {
        return new SseProgressEvent("ERROR", GenerationStage.FAILED, message, -1, null);
    }

    /**
     * Bağlantı keep-alive ping eventi.
     * LLM çağrıları uzun sürdüğünde proxy/CDN bağlantısının kopmamasını sağlar.
     * Her 20 saniyede bir gönderilir.
     */
    public static SseProgressEvent ping() {
        return new SseProgressEvent("PING", null, "keep-alive", 0, null);
    }
}
